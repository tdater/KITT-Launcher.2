package com.aiconsole.kittlauncher2.assistant

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import com.aiconsole.kittlauncher2.data.settings.SpeechInput
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Which engine turns the mic recording into text. */
enum class SpeechPath { Gemini, Device, Offline, None }

/** Picks the speech path and prepares audio for the offline model. Plain Kotlin (unit-tested). */
object SpeechRoutes {

    /**
     * Auto: Gemini when a key is set, then a safe device recognizer, then the offline model.
     * A forced choice that can't run gives [SpeechPath.None] so the driver sees why.
     */
    fun choose(input: SpeechInput, geminiKey: Boolean, deviceUsable: Boolean, offlineReady: Boolean): SpeechPath = when (input) {
        SpeechInput.Gemini -> SpeechPath.Gemini
        SpeechInput.Device -> if (deviceUsable) SpeechPath.Device else SpeechPath.None
        SpeechInput.Offline -> if (offlineReady) SpeechPath.Offline else SpeechPath.None
        SpeechInput.Auto -> when {
            geminiKey -> SpeechPath.Gemini
            deviceUsable -> SpeechPath.Device
            offlineReady -> SpeechPath.Offline
            else -> SpeechPath.None
        }
    }

    /** A device recognizer that lost the network: listen again offline (not when the driver forced Device). */
    fun retryOfflineAfterNetworkError(input: SpeechInput, offlineReady: Boolean): Boolean =
        offlineReady && input == SpeechInput.Auto

    /** 16-bit little-endian mono PCM → samples in -1..1, as the model takes them. */
    fun samples(pcm: ByteArray): FloatArray = FloatArray(pcm.size / 2) { i ->
        val lo = pcm[2 * i].toInt() and 0xFF
        val hi = pcm[2 * i + 1].toInt()
        ((hi shl 8) or lo).toShort() / 32768f
    }

    /** The model's transcript, tidied: no padding, no leftover markers like "[BLANK_AUDIO]". */
    fun clean(text: String): String = text.replace(Regex("\\[[^]]*]|<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
}

/**
 * Offline speech-to-text for the mic button: Moonshine tiny (English, about 30 MB, MIT licensed,
 * run by sherpa-onnx) downloaded on request. KITT records the clip itself (as for Gemini) and the
 * model transcribes it on the head unit, so a full question works with no signal and no Google
 * recognizer. The model is loaded on first use and freed after a few idle minutes.
 */
class OfflineSpeech(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "speech/$ARCHIVE")
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var recognizer: OfflineRecognizer? = null
    private var idleRelease: Job? = null

    private val _installed = MutableStateFlow(File(dir, READY).exists())
    val installed: StateFlow<Boolean> = _installed.asStateFlow()

    /** Download progress 0..100, or null. */
    private val _download = MutableStateFlow<Int?>(null)
    val download: StateFlow<Int?> = _download.asStateFlow()

    /** Native code missing or the model broken: stays off for this run. */
    @Volatile
    var unavailable: String? = null
        private set

    /** Installed and able to run. */
    val ready: Boolean get() = _installed.value && unavailable == null

    suspend fun install() = withContext(Dispatchers.IO) {
        val temp = File(dir.parentFile, "$ARCHIVE.part")
        temp.deleteRecursively()
        temp.mkdirs()
        _download.value = 0
        try {
            ModelArchives.download(http, URL, SHA256, BYTES, temp) { _download.value = it }
            if (!File(temp, ENCODER).exists() || !File(temp, DECODER).exists()) throw IOException("The speech model archive was incomplete")
            File(temp, "test_wavs").deleteRecursively()
            File(temp, READY).writeText(ARCHIVE)
            dir.deleteRecursively()
            if (!temp.renameTo(dir)) throw IOException("Couldn't save the speech model")
            _installed.value = true
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

    suspend fun remove() = withContext(Dispatchers.IO) {
        release()
        dir.deleteRecursively()
        _installed.value = false
    }

    /** Transcribes a 16 kHz mono PCM clip. Throws [IOException] if the model can't run. */
    suspend fun transcribe(pcm: ByteArray): String = withContext(Dispatchers.IO) {
        lock.withLock {
            idleRelease?.cancel()
            val model = load()
            val stream = model.createStream()
            val text = try {
                stream.acceptWaveform(SpeechRoutes.samples(pcm), RATE)
                model.decode(stream)
                model.getResult(stream).text
            } finally {
                stream.release()
            }
            idleRelease = scope.launch {
                delay(IDLE_RELEASE_MS)
                release()
            }
            SpeechRoutes.clean(text)
        }
    }

    /** Frees the loaded model (idle, or Android is short of memory). */
    suspend fun release() = lock.withLock {
        idleRelease?.cancel()
        idleRelease = null
        recognizer?.release()
        recognizer = null
    }

    /** From Application.onTrimMemory. */
    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) scope.launch { release() }
    }

    /** Called with [lock] held. */
    private fun load(): OfflineRecognizer {
        recognizer?.let { return it }
        unavailable?.let { throw IOException("Offline speech can't run on this head unit ($it)") }
        if (!_installed.value) throw IOException("The offline speech model isn't downloaded")
        return try {
            OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        moonshine = OfflineMoonshineModelConfig(
                            encoder = File(dir, ENCODER).path,
                            mergedDecoder = File(dir, DECODER).path
                        ),
                        tokens = File(dir, "tokens.txt").path,
                        numThreads = 2
                    )
                )
            ).also { recognizer = it }
        } catch (e: Throwable) {
            // UnsatisfiedLinkError (no native library for this CPU) or a broken model.
            Log.w(TAG, "Offline speech model failed to load", e)
            unavailable = e.message ?: e.javaClass.simpleName
            throw IOException("Offline speech can't run on this head unit ($unavailable)", e)
        }
    }

    private companion object {
        const val TAG = "OfflineSpeech"
        const val ARCHIVE = "sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27"
        const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ARCHIVE.tar.bz2"
        const val SHA256 = "9ec31b342d8fa3240c3b81b8f82e1cf7e3ac467c93ca5a999b741d5887164f8d"
        const val BYTES = 29_858_559L
        const val ENCODER = "encoder_model.ort"
        const val DECODER = "decoder_model_merged.ort"
        const val READY = ".ready"
        const val RATE = 16_000
        const val IDLE_RELEASE_MS = 5 * 60_000L
    }
}
