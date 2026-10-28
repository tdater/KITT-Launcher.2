package com.aiconsole.kittlauncher2.assistant

import com.aiconsole.kittlauncher2.BuildConfig
import com.aiconsole.kittlauncher2.ui.theme.ThemeState

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import com.aiconsole.kittlauncher2.data.settings.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * KITT's voice: one app-wide speaker shared by the assistant and proactive alerts.
 *
 * With [VoiceEngine.Gemini] each line is synthesised by Gemini's neural speech (see
 * [GeminiSpeechClient]) in sentence chunks, so playback starts while the rest is generated.
 * [VoiceEngine.OnDevice] uses a free neural voice running on the head unit ([OnDeviceVoice]),
 * which also stands in for Gemini without a key or network once a voice is downloaded.
 * The device TTS engine takes over whenever those fail.
 * Everything is queued and played on the main thread.
 */
class TextToSpeechHelper(context: Context, private val settings: KittSettings, scope: CoroutineScope) {

    private val neural = GeminiSpeechClient(context.applicationContext, settings)

    /** The free on-device neural voice and its downloadable voices (see [LocalVoices]). */
    val onDevice = OnDeviceVoice(context.applicationContext)
    private val ducker = AudioDucker(context)
    private val main = CoroutineScope(scope.coroutineContext + Dispatchers.Main)

    @Volatile
    private var ready = false
    private var preferredVoice = settings.kittVoiceName.value
    private val localReady = CompletableDeferred<Boolean>()
    private val localDone = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private lateinit var tts: TextToSpeech

    // Main thread only.
    private val queue = ArrayDeque<Utterance>()
    private var worker: Job? = null

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    /** The character a preview is speaking as right now, or null. */
    private val _speakingAs = MutableStateFlow<FilmCharacter?>(null)
    val speakingAs: StateFlow<FilmCharacter?> = _speakingAs.asStateFlow()

    private data class Utterance(val text: String, val urgent: Boolean, val deviceOnly: Boolean, val character: FilmCharacter? = null)

    init {
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            // Slightly lowered pitch and a measured rate: calmer than the engine's default.
            tts.setAudioAttributes(ducker.attributes)
            tts.setPitch(0.92f)
            tts.setSpeechRate(0.96f)
            applyVoice(preferredVoice)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finished(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    VoiceLog.add("device voice error $errorCode")
                    finished(utteranceId)
                }
            })
        }
        localReady.complete(ready)
    }

    private fun finished(utteranceId: String?) {
        utteranceId?.let { localDone.remove(it)?.complete(Unit) }
    }

    /** Offline-capable English device voices, for the fallback picker. Empty until the engine is ready. */
    fun englishVoices(): List<String> = if (!ready) emptyList() else runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired }
            .filterNot { it.features.orEmpty().contains("notInstalled") }
            .map(Voice::getName)
            .sorted()
    }.getOrDefault(emptyList())

    /** Selects the device TTS voice (the fallback when Gemini speech is on). */
    fun setVoice(name: String) {
        preferredVoice = name
        if (ready) applyVoice(name)
    }

    private fun applyVoice(name: String) {
        val voice = runCatching { tts.voices.orEmpty().firstOrNull { it.name == name } }.getOrNull()
        if (voice != null) tts.voice = voice else tts.language = Locale.getDefault()
    }

    /**
     * Queues [text]; [interrupt] drops whatever is playing or waiting first. [urgent] lines
     * (safety alerts) get a firmer delivery and fall back to the device voice much sooner.
     * [deviceOnly] skips Gemini, e.g. to preview the fallback voice.
     */
    fun speak(text: String, interrupt: Boolean = true, urgent: Boolean = false, deviceOnly: Boolean = false, character: FilmCharacter? = null) {
        if (text.isBlank()) return
        main.launch {
            if (interrupt) haltPlayback()
            queue.addLast(Utterance(text, urgent, deviceOnly, character))
            if (worker?.isActive != true) worker = main.launch { drain() }
        }
    }

    fun stop() {
        main.launch { haltPlayback() }
    }

    fun shutdown() {
        main.launch {
            haltPlayback()
            tts.shutdown()
        }
    }

    private fun haltPlayback() {
        _speakingAs.value = null
        queue.clear()
        worker?.cancel()
        worker = null
        if (ready) tts.stop()
        _speaking.value = false
    }

    private suspend fun drain() {
        com.aiconsole.kittlauncher2.data.ReturnHome.noteAudio()
        // Duck music/video for the whole queue so KITT is always heard.
        ducker.duck()
        try {
            while (true) {
                val next = queue.removeFirstOrNull() ?: break
                say(next)
            }
        } finally {
            _speaking.value = false
            _speakingAs.value = null
            ducker.release()
        }
    }

    /** The character a preview speaks as, overriding the theme's for that one line. */
    private var asCharacter: FilmCharacter? = null

    private suspend fun say(utterance: Utterance) {
        asCharacter = utterance.character
        _speakingAs.value = utterance.character
        val chunks = GeminiSpeechProtocol.chunks(GeminiSpeechProtocol.speakable(utterance.text))
        if (chunks.isEmpty()) return
        // Gemini when chosen and keyed, then the free on-device voice, then the device TTS.
        val neural = !utterance.deviceOnly && useNeural()
        val local = !utterance.deviceOnly && useOnDevice()
        VoiceLog.add(
            "line (${chunks.size} chunk${if (chunks.size == 1) "" else "s"}${if (utterance.urgent) ", urgent" else ""}): engine ${settings.voiceEngine.value}, " +
                when {
                    neural -> "Gemini"
                    local -> "offline ${settings.localVoice.value.ifEmpty { "default" }}"
                    else -> "device voice" + onDeviceSkipReason()?.let { " ($it)" }.orEmpty()
                }
        )
        var spoken = if (neural) sayNeural(chunks, utterance.urgent) else 0
        if (spoken < chunks.size && !utterance.deviceOnly && useOnDevice()) spoken += sayOnDevice(chunks.drop(spoken), utterance.urgent)
        if (spoken < chunks.size) {
            if (spoken > 0 || neural || local) VoiceLog.add("device voice speaks ${chunks.size - spoken} of ${chunks.size} chunks")
            sayLocal(chunks.drop(spoken))
        }
    }

    /** Why the offline voice isn't speaking, for the voice log. */
    private fun onDeviceSkipReason(): String? = when {
        settings.voiceEngine.value == VoiceEngine.Device -> null
        com.aiconsole.kittlauncher2.data.SafeMode.active -> "safe mode"
        onDevice.unavailable != null -> "offline voice unavailable: ${onDevice.unavailable}"
        !onDevice.isInstalled(LocalVoices.byId(settings.localVoice.value)) -> "offline voice ${LocalVoices.byId(settings.localVoice.value).id} not downloaded"
        onDevice.tooSlow.value == LocalVoices.byId(settings.localVoice.value).id -> "offline voice too slow on this unit"
        else -> null
    }

    // On-device is now the default: a driver with a Gemini key keeps Gemini until a voice is downloaded.
    private fun useNeural(): Boolean = settings.geminiKey.isNotEmpty() && when (settings.voiceEngine.value) {
        VoiceEngine.Gemini -> true
        VoiceEngine.OnDevice -> !onDeviceReady()
        VoiceEngine.Device -> false
    }

    /** The free offline voice: chosen, or standing in for Gemini; only once a voice is downloaded. */
    private fun useOnDevice(): Boolean = settings.voiceEngine.value != VoiceEngine.Device && onDeviceReady()

    private fun onDeviceReady(): Boolean = !com.aiconsole.kittlauncher2.data.SafeMode.active &&
        onDevice.unavailable == null && LocalVoices.byId(settings.localVoice.value).let { onDevice.isInstalled(it) && onDevice.tooSlow.value != it.id }

    /** Plays chunks through the on-device voice, synthesizing the next while one plays. Returns how many were spoken. */
    private suspend fun sayOnDevice(chunks: List<String>, urgent: Boolean): Int = supervisorScope {
        val film = filmCharacter()
        // The character's own voice if it's already downloaded and keeping up, else the driver's choice.
        val preferred = if (!BuildConfig.HOLO_DESIGN) null else film?.let { LocalVoices.byId(it.localVoiceHint) }
            ?.takeIf { onDevice.isInstalled(it) && onDevice.tooSlow.value != it.id }
        val voice = preferred ?: LocalVoices.byId(settings.localVoice.value)
        val speaker = if (preferred != null) 0 else settings.localVoiceSpeaker.value
        val lines = if (BuildConfig.HOLO_DESIGN) chunks.map { film?.accentText(it) ?: it } else chunks
        val speed = (film?.rate ?: 1f) * (if (urgent) 1.06f else 1f)
        // The characters' pitches are tuned for the device voice; a neural voice needs less.
        val pitch = film?.let { 1f + (it.pitch - 1f) * 0.7f } ?: 1f
        // Urgent lines (overheating, a new code) mustn't wait on a slow model load: device voice after 3 s.
        val timeout = if (urgent) URGENT_TIMEOUT_MS else ON_DEVICE_TIMEOUT_MS
        fun fetch(index: Int) = async(Dispatchers.IO) { onDevice.synthesize(voice, lines[index], speaker, speed) }

        var next = fetch(0)
        for (index in chunks.indices) {
            val clip = try {
                withTimeout(timeout) { next.await() }
            } catch (e: TimeoutCancellationException) {
                next.cancel()
                Log.w(TAG, "On-device voice timed out; using device voice")
                onDevice.timedOut(voice)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "On-device voice failed; using device voice: ${e.message}")
                VoiceLog.add("${voice.id}: synthesis failed: ${e.message ?: e.javaClass.simpleName}")
                null
            } ?: return@supervisorScope index
            if (index + 1 < chunks.size) next = fetch(index + 1)
            _speaking.value = true
            try {
                onDevice.play(clip, ducker.attributes, pitch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A unit whose audio stack refuses float PCM must not crash the launcher.
                Log.w(TAG, "On-device playback failed; using device voice", e)
                VoiceLog.add("${voice.id}: playback failed: ${e.message ?: e.javaClass.simpleName}")
                next.cancel()
                return@supervisorScope index
            }
        }
        chunks.size
    }

    /** Plays chunks through Gemini speech, prefetching the next while one plays. Returns how many were spoken. */
    private suspend fun sayNeural(chunks: List<String>, urgent: Boolean): Int = supervisorScope {
        val film = filmCharacter()
        val voice = film?.geminiVoice ?: settings.geminiVoice.value
        val baseStyle = film?.style ?: GeminiSpeechProtocol.STYLE
        val style = if (urgent) "$baseStyle For this line: firm, focused and urgent, still in character." else baseStyle
        val timeout = if (urgent) URGENT_TIMEOUT_MS else CHUNK_TIMEOUT_MS
        fun fetch(index: Int) = async(Dispatchers.IO) { neural.synthesize(chunks[index], voice, style) }

        var next = fetch(0)
        for (index in chunks.indices) {
            val file = try {
                withTimeout(timeout) { next.await() }
            } catch (e: TimeoutCancellationException) {
                next.cancel()
                Log.w(TAG, "Gemini speech timed out; using device voice")
                return@supervisorScope index
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini speech failed; using device voice: ${e.message}")
                return@supervisorScope index
            }
            if (index + 1 < chunks.size) next = fetch(index + 1)
            _speaking.value = true
            try {
                play(file)
            } catch (e: IOException) {
                Log.w(TAG, "Playback failed; using device voice", e)
                next.cancel()
                return@supervisorScope index
            }
        }
        chunks.size
    }

    private suspend fun sayLocal(chunks: List<String>) {
        val ok = withTimeoutOrNull(LOCAL_INIT_TIMEOUT_MS) { localReady.await() } ?: false
        if (!ok) {
            VoiceLog.add("device voice not ready: no TTS engine answered")
            return // No TTS engine installed; the answer is still shown on screen.
        }
        // Film characters get their own pitch and pace on the device voice too.
        val film = filmCharacter()
        tts.setPitch(film?.pitch?.times(0.92f) ?: 0.92f)
        tts.setSpeechRate(film?.rate?.times(0.96f) ?: 0.96f)
        for (chunk in chunks) {
            val id = "kitt-${System.nanoTime()}"
            val done = CompletableDeferred<Unit>()
            localDone[id] = done
            try {
                _speaking.value = true
                if (tts.speak(if (BuildConfig.HOLO_DESIGN) film?.accentText(chunk) ?: chunk else chunk, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) return
                done.await()
            } finally {
                localDone.remove(id)
            }
        }
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer()
        var released = false
        fun release() {
            if (!released) {
                released = true
                player.release()
            }
        }
        player.setAudioAttributes(ducker.attributes)
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener {
            release()
            if (continuation.isActive) continuation.resume(Unit)
        }
        player.setOnErrorListener { _, what, extra ->
            release()
            if (continuation.isActive) continuation.resumeWithException(IOException("MediaPlayer error $what/$extra"))
            true
        }
        // Cancellation comes from haltPlayback on the main thread, same as the player callbacks.
        continuation.invokeOnCancellation { release() }
        try {
            player.setDataSource(file.path)
            player.prepareAsync()
        } catch (e: Exception) {
            release()
            continuation.resumeWithException(IOException("Couldn't play ${file.name}", e))
        }
    }

    private fun filmCharacter(): FilmCharacter? =
        asCharacter ?: if (settings.filmCharacters.value) ThemeState.filmCharacter else null

    private companion object {
        const val TAG = "KittVoice"
        const val CHUNK_TIMEOUT_MS = 10_000L
        const val URGENT_TIMEOUT_MS = 3_000L
        // The first line after start-up also loads the model, slow for Kokoro on weak units.
        const val ON_DEVICE_TIMEOUT_MS = 20_000L
        const val LOCAL_INIT_TIMEOUT_MS = 5_000L
    }
}
