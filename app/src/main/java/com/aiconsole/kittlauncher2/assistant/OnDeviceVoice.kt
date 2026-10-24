package com.aiconsole.kittlauncher2.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One synthesized line: mono float PCM. */
class VoiceClip(val samples: FloatArray, val sampleRate: Int)

/**
 * KITT's free on-device neural voice: open Piper / Kokoro voices run by sherpa-onnx on the head
 * unit itself, so there's no API key, no cost and no internet once a voice is downloaded. Voices
 * ([LocalVoices]) download on request into filesDir/voices/<id>. Synthesis runs on a background
 * thread, one line at a time; [play] plays a clip with the film character's pitch.
 */
class OnDeviceVoice(context: Context) {

    private val root = File(context.applicationContext.filesDir, "voices")
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private val _installed = MutableStateFlow(scanInstalled())
    /** Ids of the voices on the unit. */
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    /** The voice being downloaded and how far along (0..100), or null. */
    private val _download = MutableStateFlow<Pair<String, Int>?>(null)
    val download: StateFlow<Pair<String, Int>?> = _download.asStateFlow()

    private val engineLock = Mutex()
    private var engine: OfflineTts? = null
    private var engineId: String? = null

    /** Native code missing or the model broken: the device voice takes over for good this run. */
    @Volatile
    var unavailable: String? = null
        private set

    fun isInstalled(voice: LocalVoice): Boolean = voice.id in _installed.value

    private fun scanInstalled(): Set<String> =
        root.listFiles().orEmpty().filter { File(it, READY).exists() }.map { it.name }.toSet()

    /** Downloads and unpacks [voice] (about 20 MB, the Kokoro pack about 100 MB). */
    suspend fun install(voice: LocalVoice) = withContext(Dispatchers.IO) {
        val target = File(root, voice.id)
        val temp = File(root, "${voice.id}.part")
        temp.deleteRecursively()
        temp.mkdirs()
        _download.value = voice.id to 0
        try {
            ModelArchives.download(http, voice.url, voice.sha256, voice.bytes.takeIf { it > 0 } ?: (voice.sizeMb * 1_048_576L), temp) { percent ->
                _download.value = voice.id to percent
            }
            if (temp.listFiles().orEmpty().none { it.name.endsWith(".onnx") }) throw IOException("The voice archive had no model in it")
            File(temp, READY).writeText(voice.archive)
            target.deleteRecursively()
            if (!temp.renameTo(target)) throw IOException("Couldn't save the voice")
            _installed.value = scanInstalled()
        } catch (e: CancellationException) {
            temp.deleteRecursively()
            throw e
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw if (e is IOException) e else IOException(e.message ?: e.javaClass.simpleName, e)
        } finally {
            _download.value = null
        }
    }

    suspend fun remove(voice: LocalVoice) = withContext(Dispatchers.IO) {
        engineLock.withLock {
            if (engineId == voice.id) {
                engine?.release()
                engine = null
                engineId = null
            }
        }
        File(root, voice.id).deleteRecursively()
        _installed.value = scanInstalled()
    }

    /** Synthesizes one line, or null if the voice isn't installed or can't run here. */
    suspend fun synthesize(voice: LocalVoice, text: String, speaker: Int, speed: Float): VoiceClip? {
        if (unavailable != null || !isInstalled(voice)) return null
        return withContext(Dispatchers.IO) {
            engineLock.withLock {
                idleRelease?.cancel()
                val tts = load(voice) ?: return@withLock null
                val started = System.currentTimeMillis()
                val audio = tts.generate(text, sid = LocalVoices.speaker(voice, speaker), speed = speed)
                val audioMs = if (audio.sampleRate > 0) audio.samples.size * 1000L / audio.sampleRate else 0L
                VoiceLog.add("${voice.id}: ${VoiceLog.speed(audioMs, System.currentTimeMillis() - started)}, ${text.length} chars")
                // The model holds 60-300 MB: let it go after a few quiet minutes (it reloads in a second or two).
                idleRelease = scope.launch {
                    delay(IDLE_RELEASE_MS)
                    release()
                }
                VoiceClip(audio.samples, audio.sampleRate).takeIf { it.samples.isNotEmpty() }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleRelease: Job? = null

    /** Frees the loaded model (idle, or Android is short of memory). */
    suspend fun release() = engineLock.withLock {
        idleRelease?.cancel()
        idleRelease = null
        engine?.release()
        engine = null
        engineId = null
    }

    /** From Application.onTrimMemory: a loaded model goes when memory runs low. */
    fun trimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) scope.launch { release() }
    }

    /** Loads (or reuses) the voice's model. Called with [engineLock] held. */
    private fun load(voice: LocalVoice): OfflineTts? {
        if (engineId == voice.id) engine?.let { return it }
        engine?.release()
        engine = null
        engineId = null
        val dir = File(root, voice.id)
        val model = dir.listFiles().orEmpty().firstOrNull { it.name.endsWith(".onnx") } ?: return null
        val tokens = File(dir, "tokens.txt").path
        val data = File(dir, "espeak-ng-data").path
        val voices = File(dir, "voices.bin")
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        val modelConfig = if (voices.exists()) {
            OfflineTtsModelConfig(kokoro = OfflineTtsKokoroModelConfig(model = model.path, voices = voices.path, tokens = tokens, dataDir = data), numThreads = threads)
        } else {
            OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(model = model.path, tokens = tokens, dataDir = data), numThreads = threads)
        }
        val started = System.currentTimeMillis()
        return try {
            OfflineTts(config = OfflineTtsConfig(model = modelConfig)).also {
                engine = it
                engineId = voice.id
                VoiceLog.add("${voice.id}: model loaded in ${System.currentTimeMillis() - started} ms ($threads threads)")
            }
        } catch (e: Throwable) {
            // UnsatisfiedLinkError (no native library for this CPU) or a broken model.
            Log.w(TAG, "On-device voice unavailable", e)
            unavailable = e.message ?: e.javaClass.simpleName
            VoiceLog.add("${voice.id}: model failed to load: $unavailable")
            null
        }
    }

    private val _tooSlow = MutableStateFlow<String?>(null)
    /** Id of a voice that couldn't keep up on this unit this run; the device voice speaks instead. */
    val tooSlow: StateFlow<String?> = _tooSlow.asStateFlow()
    private var timeouts = 0 to ""

    /**
     * A line timed out waiting for [voice]. The first one may just be the model loading; after
     * two the voice is benched for this run, so queued alerts don't each wait out a timeout with
     * the music ducked.
     */
    fun timedOut(voice: LocalVoice) {
        val count = if (timeouts.second == voice.id) timeouts.first + 1 else 1
        timeouts = count to voice.id
        VoiceLog.add("${voice.id}: timed out (${count}x)")
        if (count >= SLOW_STRIKES) _tooSlow.value = voice.id
    }

    /**
     * Plays [clip] to the end (cancellable), [pitch] 1 = natural. Streams 16-bit PCM: some head
     * units (an Android 12 alps build, 2026-10) accepted a static float track, pulled a few
     * milliseconds of it and stalled, so KITT ducked the music and said nothing. A track whose
     * play position stops moving throws, and the device voice takes over.
     */
    suspend fun play(clip: VoiceClip, attributes: AudioAttributes, pitch: Float) = withContext(Dispatchers.IO) {
        val pcm = VoicePcm.toPcm16(clip.samples)
        val minBuffer = AudioTrack.getMinBufferSize(clip.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(clip.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, clip.sampleRate / 2 * 2)) // About half a second.
            .build()
        try {
            if (track.state != AudioTrack.STATE_INITIALIZED) throw stalled("the audio track didn't open")
            if (pitch != 1f) runCatching { track.playbackParams = PlaybackParams().setPitch(pitch).setSpeed(1f) }
            track.play()
            val watch = VoicePcm.StallWatch(STALL_MS)
            var written = 0
            while (written < pcm.size && isActive) {
                // Non-blocking, so a stalled sink can't hang this thread in write().
                val n = track.write(pcm, written, minOf(pcm.size - written, WRITE_CHUNK), AudioTrack.WRITE_NON_BLOCKING)
                if (n < 0) throw stalled("write error $n")
                written += n
                if (watch.stalled(track.playbackHeadPosition, System.currentTimeMillis())) throw stalled("no audio came out")
                if (n == 0) delay(20)
            }
            val limit = System.currentTimeMillis() + pcm.size * 1000L / clip.sampleRate + 2_000
            while (isActive && track.playbackHeadPosition < pcm.size && System.currentTimeMillis() < limit) {
                if (watch.stalled(track.playbackHeadPosition, System.currentTimeMillis())) throw stalled("no audio came out")
                delay(40)
            }
            VoiceLog.add("played ${track.playbackHeadPosition}/${pcm.size} frames at ${clip.sampleRate} Hz, routed to ${runCatching { track.routedDevice?.productName }.getOrNull() ?: "?"}")
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
        }
    }

    private fun stalled(why: String): IOException {
        unavailable = "this head unit didn't play the voice's audio: $why"
        VoiceLog.add("playback stalled: $why")
        return IOException("On-device playback stalled: $why")
    }

    private companion object {
        const val IDLE_RELEASE_MS = 5 * 60_000L
        const val TAG = "OnDeviceVoice"
        const val READY = ".ready"
        /** Play position frozen this long means the sink isn't taking audio. */
        const val STALL_MS = 1_500L
        const val WRITE_CHUNK = 4_096
        const val SLOW_STRIKES = 2
    }
}
