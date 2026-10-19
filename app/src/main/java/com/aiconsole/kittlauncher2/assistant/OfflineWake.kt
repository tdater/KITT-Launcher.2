package com.aiconsole.kittlauncher2.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the offline spotter heard. */
sealed interface OfflineHeard {
    data object Wake : OfflineHeard
    /** "KITT, pause music": [text] is the command for VoiceCommands.parse. */
    data class Command(val text: String) : OfflineHeard
}

/**
 * The phrases the offline spotter listens for. "Hey KITT" opens KITT; "KITT, <command>" runs one
 * of [COMMANDS] straight away, with no internet. Plain Kotlin (unit-tested).
 */
object OfflinePhrases {
    private val WAKE = listOf("HEY KITT", "HEY KIT")
    private val CALLS = listOf("KITT", "KIT")

    /** Spoken after "KITT," → the text VoiceCommands parses. */
    val COMMANDS: List<Pair<String, String>> = listOf(
        "PAUSE MUSIC" to "pause", "PAUSE" to "pause", "PLAY MUSIC" to "play", "RESUME MUSIC" to "play",
        "NEXT SONG" to "next track", "NEXT TRACK" to "next track", "SKIP SONG" to "next track",
        "PREVIOUS SONG" to "previous track", "PREVIOUS TRACK" to "previous track",
        "FIND GAS" to "find gas", "WHERE DID I PARK" to "where did i park",
        "PURSUIT MODE" to "pursuit mode", "CANCEL THE ROUTE" to "cancel the route", "STOP NAVIGATION" to "stop navigation",
        "READ THE CODES" to "read codes", "CHECK ENGINE CODES" to "check engine codes",
        "SHOW THE MAP" to "show the map", "SHOW GAUGES" to "show gauges", "SHOW MEDIA" to "show media",
        "START LOGGING" to "start logging", "STOP LOGGING" to "stop logging"
    )

    /** The spotter's keywords file: one "pieces @TAG" line per spelling. Phrases the model can't spell are left out. */
    fun keywords(encode: (String) -> String?): String = buildList {
        WAKE.mapNotNull(encode).distinct().forEach { add("$it @WAKE") }
        COMMANDS.forEachIndexed { index, (phrase, _) ->
            CALLS.mapNotNull { encode("$it $phrase") }.distinct().forEach { add("$it @CMD_$index") }
        }
    }.joinToString("\n")

    /** The spotter's result (its tag) → what was heard. */
    fun heard(keyword: String): OfflineHeard? {
        val tag = keyword.trim().removePrefix("@").uppercase()
        if (tag == "WAKE") return OfflineHeard.Wake
        val index = tag.removePrefix("CMD_").toIntOrNull() ?: return null
        return COMMANDS.getOrNull(index)?.let { OfflineHeard.Command(it.second) }
    }
}

/**
 * Offline "Hey KITT" and quick commands: a small keyword-spotting model (sherpa-onnx, Apache-2.0,
 * about 15 MB, downloaded on request) listens to the microphone on the head unit itself. It needs
 * no internet, no Google recognizer and no audio focus, so it never chimes or pauses music.
 */
class OfflineWake(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "wake/$ARCHIVE")
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())

    private val _installed = MutableStateFlow(File(dir, READY).exists())
    val installed: StateFlow<Boolean> = _installed.asStateFlow()

    /** Download progress 0..100, or null. */
    private val _download = MutableStateFlow<Int?>(null)
    val download: StateFlow<Int?> = _download.asStateFlow()

    /** Native code missing or the model broken: stays off for this run. */
    @Volatile
    var unavailable: String? = null
        private set

    /** Bumped on every start and stop: a listening thread runs only while its number is current. */
    @Volatile
    private var generation = 0
    private var active = false // main thread

    suspend fun install() = withContext(Dispatchers.IO) {
        val temp = File(dir.parentFile, "$ARCHIVE.part")
        temp.deleteRecursively()
        temp.mkdirs()
        _download.value = 0
        try {
            ModelArchives.download(http, URL, SHA256, BYTES, temp) { _download.value = it }
            if (!File(temp, ENCODER).exists() || !File(temp, "bpe.model").exists()) throw IOException("The wake-word archive was incomplete")
            File(temp, READY).writeText(ARCHIVE)
            dir.deleteRecursively()
            if (!temp.renameTo(dir)) throw IOException("Couldn't save the wake-word model")
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

    fun remove() {
        stop()
        dir.deleteRecursively()
        _installed.value = false
    }

    /**
     * Starts listening (no-op if already). [onHeard] and [onFailed] (the mic or model gave out)
     * run on the main thread. Main thread only; needs RECORD_AUDIO.
     */
    fun start(onHeard: (OfflineHeard) -> Unit, onFailed: () -> Unit): Boolean {
        if (active) return true
        if (!_installed.value || unavailable != null) return false
        active = true
        val gen = ++generation
        Thread({ listen(gen, onHeard, onFailed) }, "kitt-wake").apply {
            isDaemon = true
            start()
        }
        return true
    }

    fun stop() {
        active = false
        generation++
    }

    @SuppressLint("MissingPermission") // Checked by WakeWordListener before starting.
    private fun listen(gen: Int, onHeard: (OfflineHeard) -> Unit, onFailed: () -> Unit) {
        var spotter: KeywordSpotter? = null
        var record: AudioRecord? = null
        try {
            val keywords = File(dir, "keywords-kitt.txt")
            val tokenizer = PieceTokenizer.fromModel(File(dir, "bpe.model").readBytes())
            keywords.writeText(OfflinePhrases.keywords(tokenizer::encode))
            spotter = KeywordSpotter(
                config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = File(dir, ENCODER).path,
                            decoder = File(dir, DECODER).path,
                            joiner = File(dir, JOINER).path
                        ),
                        tokens = File(dir, "tokens.txt").path,
                        numThreads = 1,
                        modelType = "zipformer2"
                    ),
                    keywordsFile = keywords.path
                )
            )
            val stream = spotter.createStream()
            val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, CHUNK * 4))
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IOException("the microphone didn't open")
            record.startRecording()
            val pcm = ShortArray(CHUNK)
            val samples = FloatArray(CHUNK)
            while (generation == gen) {
                val n = record.read(pcm, 0, CHUNK)
                if (n <= 0) {
                    if (n < 0) throw IOException("microphone read failed ($n)")
                    continue
                }
                for (i in 0 until n) samples[i] = pcm[i] / 32768f
                stream.acceptWaveform(if (n == CHUNK) samples else samples.copyOf(n), RATE)
                while (spotter.isReady(stream)) spotter.decode(stream)
                val keyword = spotter.getResult(stream).keyword
                if (keyword.isNotBlank()) {
                    spotter.reset(stream)
                    OfflinePhrases.heard(keyword)?.let { heard -> main.post { if (generation == gen) onHeard(heard) } }
                }
            }
            stream.release()
        } catch (e: Throwable) {
            // UnsatisfiedLinkError (no native library for this CPU), a broken model or a busy mic.
            if (generation == gen) {
                Log.w(TAG, "Offline wake word stopped", e)
                if (e !is IOException) unavailable = e.message ?: e.javaClass.simpleName
                main.post {
                    if (generation == gen) {
                        active = false
                        onFailed()
                    }
                }
            }
        } finally {
            runCatching { record?.stop() }
            record?.release()
            spotter?.release()
        }
    }

    private companion object {
        const val TAG = "OfflineWake"
        const val ARCHIVE = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile"
        const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/$ARCHIVE.tar.bz2"
        const val SHA256 = "2e6ac2577310bfa2f4b6b5fab0478b868c9d0b2cb2c51b3e13b50581b588864d"
        const val BYTES = 15_667_804L
        const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.onnx"
        const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
        const val READY = ".ready"
        const val RATE = 16_000
        const val CHUNK = 1_600 // 100 ms
    }
}
