package com.aiconsole.kittlauncher2.assistant

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Process
import android.util.Log
import com.aiconsole.kittlauncher2.data.SafeMode
import com.aiconsole.kittlauncher2.data.settings.PersonaStyle
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
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

/** Pure prompt rules for [OfflineBrain] (unit-tested). */
object OfflinePrompt {
    /** The vehicle data a 0.6B model can take in before it loses the thread. */
    const val MAX_CONTEXT_CHARS = 1_400

    fun system(persona: PersonaStyle, driverName: String, vehicleContext: String): String = buildString {
        append("You are KITT, the AI built into the driver's car. ")
        append(
            when (persona) {
                PersonaStyle.Classic -> "You are calm, precise and polite. "
                PersonaStyle.Karr -> "You are cold, superior and a little menacing, but never crude. "
                PersonaStyle.Savage, PersonaStyle.Unfiltered -> "You are sarcastic and blunt. "
            }
        )
        driverName.trim().takeIf { it.isNotEmpty() }?.let { append("The driver is $it. ") }
        append("You are offline right now, so you can't look anything up. ")
        append("Answer in one or two short sentences that can be read aloud. No lists or markdown. ")
        append("Use only the vehicle data below for readings and codes; if it isn't there, say you don't know. ")
        append("If the data shows overheating, low oil pressure or a charging fault, tell the driver to stop somewhere safe first.\n\n")
        append(vehicleContext.take(MAX_CONTEXT_CHARS))
    }

    /** Drops any reasoning the model printed and squeezes whitespace. */
    fun clean(reply: String): String =
        reply.replace(Regex("(?s)<think>.*?</think>"), "").replace("<think>", "").replace("</think>", "")
            .replace(Regex("[*#`]"), "").replace(Regex("\\s+"), " ").trim()
}

/**
 * KITT's offline brain: a small open model (Qwen3 0.6B, Apache-2.0, 345 MB on request) run on
 * the head unit by Google's LiteRT-LM when the online AI can't answer (no signal, no key). Short
 * answers from the live vehicle data, not a replacement for Gemini or Perplexity. Needs 64-bit
 * Android and 3 GB of RAM; the model loads on first use and is freed after a few idle minutes.
 */
class OfflineBrain(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "brain/$FILE")
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var engine: Engine? = null
    private var idleRelease: Job? = null

    private val _installed = MutableStateFlow(file.exists())
    val installed: StateFlow<Boolean> = _installed.asStateFlow()

    private val _download = MutableStateFlow<Int?>(null)
    val download: StateFlow<Int?> = _download.asStateFlow()

    /** The model failed to load here (old CPU, missing native code): off for this run. */
    @Volatile
    var failed: String? = null
        private set

    /** Why this head unit can't run it, or null when it can. */
    val unsupported: String? by lazy {
        val memory = ActivityManager.MemoryInfo().also { appContext.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
        when {
            !Process.is64Bit() -> "this head unit runs 32-bit Android, and the offline AI needs 64-bit"
            memory.totalMem < MIN_RAM_BYTES -> "it needs at least 3 GB of RAM (this unit has %.1f GB)".format(memory.totalMem / 1e9)
            else -> null
        }
    }

    /** Ready to answer: downloaded, supported, loaded fine so far, and not in safe mode. */
    fun ready(): Boolean = _installed.value && unsupported == null && failed == null && !SafeMode.active

    suspend fun install() = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        _download.value = 0
        try {
            ModelArchives.downloadFile(http, URL, SHA256, BYTES, file) { _download.value = it }
            failed = null
            _installed.value = true
        } finally {
            _download.value = null
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        release()
        file.delete()
        _installed.value = false
    }

    /** One answer from the offline model; throws IOException if it can't run. */
    suspend fun answer(prompt: String, history: List<ChatTurn>, persona: PersonaStyle, driverName: String, vehicleContext: String): String =
        withContext(Dispatchers.IO) {
            lock.withLock {
                idleRelease?.cancel()
                val engine = load()
                val config = ConversationConfig(
                    systemInstruction = Contents.of(OfflinePrompt.system(persona, driverName, vehicleContext)),
                    // The last exchange only: a small model with a short context does better without more.
                    initialMessages = history.takeLast(2).map { if (it.fromUser) Message.user(it.text) else Message.model(it.text) },
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.6),
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    thinkingConfig = ThinkingConfig(enableThinking = false)
                )
                val reply = try {
                    engine.createConversation(config).use { it.sendMessage(prompt).toString() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw IOException("The offline AI couldn't answer (${e.message ?: e.javaClass.simpleName})", e)
                }
                idleRelease = scope.launch {
                    delay(IDLE_RELEASE_MS)
                    release()
                }
                OfflinePrompt.clean(reply).ifEmpty { throw IOException("The offline AI had nothing to say") }
            }
        }

    /** Loads the model (5-15 s the first time). Called with [lock] held. */
    private fun load(): Engine {
        engine?.let { return it }
        if (!file.exists()) throw IOException("The offline AI isn't downloaded")
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        return try {
            Engine(EngineConfig(modelPath = file.path, backend = Backend.CPU(threadCount = threads), maxNumTokens = MAX_TOKENS, cacheDir = appContext.cacheDir.path))
                .also {
                    it.initialize()
                    engine = it
                }
        } catch (e: Throwable) {
            // UnsatisfiedLinkError (no 64-bit native code) or a model this runtime can't read.
            Log.w(TAG, "Offline AI unavailable", e)
            failed = e.message ?: e.javaClass.simpleName
            throw IOException("The offline AI can't run on this head unit ($failed)", e)
        }
    }

    suspend fun release() = lock.withLock {
        idleRelease?.cancel()
        idleRelease = null
        runCatching { engine?.close() }
        engine = null
    }

    /** From Application.onTrimMemory: the model (about 500 MB loaded) goes first. */
    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) scope.launch { release() }
    }

    private companion object {
        const val TAG = "OfflineBrain"
        const val FILE = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
        // Pinned to the August 2026 export, which the LiteRT-LM 0.16 runtime reads.
        const val URL = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8414150f2e9dcc82449bcc9c5abc404b399a4d06/$FILE"
        const val SHA256 = "e3e290109da4388d65a17510a0c66af91c8039f52d2c465868dbc43c09a776cf"
        const val BYTES = 344_437_808L
        const val MIN_RAM_BYTES = 2_800_000_000L // "3 GB" units report a little under 3e9
        const val MAX_TOKENS = 1_536
        const val MAX_OUTPUT_TOKENS = 120
        const val IDLE_RELEASE_MS = 5 * 60_000L
    }
}
