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
                val audio = tts.generate(text, sid = LocalVoices.speaker(voice, speaker), speed = speed)
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
        return try {
            OfflineTts(config = OfflineTtsConfig(model = modelConfig)).also {
                engine = it
                engineId = voice.id
            }
        } catch (e: Throwable) {
            // UnsatisfiedLinkError (no native library for this CPU) or a broken model.
            Log.w(TAG, "On-device voice unavailable", e)
            unavailable = e.message ?: e.javaClass.simpleName
            null
        }
    }

    /** Plays [clip] to the end (cancellable), [pitch] 1 = natural. */
    suspend fun play(clip: VoiceClip, attributes: AudioAttributes, pitch: Float) = withContext(Dispatchers.IO) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(clip.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(clip.samples.size * 4)
            .build()
        try {
            track.write(clip.samples, 0, clip.samples.size, AudioTrack.WRITE_BLOCKING)
            if (pitch != 1f) runCatching { track.playbackParams = PlaybackParams().setPitch(pitch).setSpeed(1f) }
            track.play()
            val frames = clip.samples.size
            val limit = System.currentTimeMillis() + frames * 1000L / clip.sampleRate * 3 + 2_000
            while (isActive && track.playbackHeadPosition < frames && System.currentTimeMillis() < limit) delay(40)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val IDLE_RELEASE_MS = 5 * 60_000L
        const val TAG = "OnDeviceVoice"
        const val READY = ".ready"
    }
}
