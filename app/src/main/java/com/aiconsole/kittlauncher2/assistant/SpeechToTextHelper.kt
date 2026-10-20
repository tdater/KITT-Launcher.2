package com.aiconsole.kittlauncher2.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import com.aiconsole.kittlauncher2.data.settings.SpeechInput
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SpeechState {
    data object Idle : SpeechState
    data class Listening(val level: Float = 0f, val partial: String = "") : SpeechState
    data object Processing : SpeechState
    data class Result(val text: String) : SpeechState
    data class Error(val message: String) : SpeechState
}

/**
 * Wraps the platform [SpeechRecognizer]. The framework requires every call to happen on the
 * main thread; audio capture and recognition run in the recognition service's own process,
 * so nothing heavy happens on the UI thread. Callbacks just update [state].
 */
class SpeechToTextHelper(
    context: Context,
    private val settings: KittSettings,
    private val offline: OfflineSpeech
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var boundTarget: RecognizerTarget? = null
    private val transcriber = GeminiTranscriber(settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var geminiJob: Job? = null
    private var offlineRetried = false

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    init {
        // The head unit took the screen when the mic opened: stop now so it can't loop.
        scope.launch {
            MicGuard.stops.collect {
                geminiJob?.cancel()
                recognizer?.cancel()
                MicGuard.listeningEnded()
                _state.value = SpeechState.Error(
                    "The head unit switched apps when the microphone opened. See KITT Config → Microphone fix."
                )
            }
        }
    }

    fun start() = onMain {
        offlineRetried = false
        val target = SpeechRecognizers.resolve(appContext, settings.speechRecognizer.value)
        val deviceUsable = target != RecognizerTarget.Unavailable &&
            (target != RecognizerTarget.SystemDefault || SpeechRecognizer.isRecognitionAvailable(appContext))
        when (SpeechRoutes.choose(settings.speechInput.value, settings.geminiKey.isNotEmpty(), deviceUsable, offline.ready)) {
            SpeechPath.Gemini -> startRecording(gemini = true)
            SpeechPath.Offline -> startRecording(gemini = false)
            SpeechPath.Device -> startDevice(target)
            SpeechPath.None -> _state.value = SpeechState.Error(noPathMessage())
        }
    }

    private fun noPathMessage(): String = when (settings.speechInput.value) {
        SpeechInput.Offline ->
            offline.unavailable?.let { "Offline speech can't run on this head unit ($it)." }
                ?: "Download KITT offline speech first (KITT Config → Speech input)."
        SpeechInput.Device ->
            "Voice input isn't available on this head unit. Pick KITT offline in KITT Config → Speech input, or type your question."
        else -> "No safe speech recognizer here. Download KITT offline speech or add a Gemini key in KITT Config, or pick a recognizer there."
    }

    private fun startDevice(target: RecognizerTarget) {
        if (target != boundTarget) {
            recognizer?.destroy()
            recognizer = null
        }
        val active = recognizer ?: SpeechRecognizers.create(appContext, target).also {
            it.setRecognitionListener(this)
            recognizer = it
            boundTarget = target
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        _state.value = SpeechState.Listening()
        MicGuard.listeningStarted()
        active.startListening(intent)
    }

    /**
     * KITT's own path: record the mic, then Gemini or the offline model transcribes. No system
     * recognizer involved. If Gemini can't be reached, the same clip goes to the offline model.
     */
    private fun startRecording(gemini: Boolean, prompt: String = "") {
        if (gemini && settings.geminiKey.isEmpty()) {
            _state.value = SpeechState.Error("KITT's speech input needs a Gemini key (KITT Config).")
            return
        }
        geminiJob?.cancel()
        _state.value = SpeechState.Listening(partial = prompt)
        MicGuard.listeningStarted()
        geminiJob = scope.launch {
            _state.value = try {
                val pcm = try {
                    transcriber.record { level ->
                        (_state.value as? SpeechState.Listening)?.let { _state.value = it.copy(level = level) }
                    }
                } finally {
                    MicGuard.listeningEnded()
                }
                if (pcm == null) {
                    SpeechState.Error("Didn't catch that.")
                } else {
                    _state.value = SpeechState.Processing
                    transcribe(pcm, gemini).let { if (it.isBlank()) SpeechState.Error("Didn't catch that.") else SpeechState.Result(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SpeechState.Error("Microphone permission is needed for voice input.")
            } catch (e: IOException) {
                SpeechState.Error(e.message ?: "Voice input failed.")
            }
        }
    }

    private suspend fun transcribe(pcm: ByteArray, gemini: Boolean): String {
        if (!gemini) return offline.transcribe(pcm)
        return try {
            transcriber.transcribe(pcm)
        } catch (e: IOException) {
            if (!offline.ready) throw e
            offline.transcribe(pcm)
        }
    }

    /** Stop capturing and deliver whatever was heard. */
    fun stop() = onMain {
        if (geminiJob?.isActive == true) transcriber.requestStop() else recognizer?.stopListening()
    }

    /** Abort without a result. */
    fun cancel() = onMain {
        geminiJob?.cancel()
        recognizer?.cancel()
        _state.value = SpeechState.Idle
    }

    fun reset() {
        _state.value = SpeechState.Idle
    }

    fun release() = onMain {
        geminiJob?.cancel()
        recognizer?.destroy()
        recognizer = null
        boundTarget = null
    }


    override fun onReadyForSpeech(params: Bundle?) {
        _state.value = SpeechState.Listening()
    }

    override fun onRmsChanged(rmsdB: Float) {
        val current = _state.value as? SpeechState.Listening ?: return
        // rmsdB typically ranges about -2..10
        _state.value = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val current = _state.value as? SpeechState.Listening ?: return
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (!text.isNullOrBlank()) _state.value = current.copy(partial = text)
    }

    override fun onEndOfSpeech() {
        MicGuard.listeningEnded()
        _state.value = SpeechState.Processing
    }

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        _state.value = if (text.isNullOrBlank()) SpeechState.Error("Didn't catch that.") else SpeechState.Result(text)
    }

    override fun onError(error: Int) {
        MicGuard.listeningEnded()
        val network = error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
        if (network && !offlineRetried && SpeechRoutes.retryOfflineAfterNetworkError(settings.speechInput.value, offline.ready)) {
            // The recognizer needed a signal: listen again with the offline model.
            offlineRetried = true
            startRecording(gemini = false, prompt = "No signal. Listening offline, say it again.")
            return
        }
        _state.value = SpeechState.Error(errorMessage(error))
    }

    override fun onBeginningOfSpeech() = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Voice recognition needs a connection on this unit. Type your question instead."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice engine is busy. Try again."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_CLIENT ->
            "Speech recognizer stopped. Pick a different one in KITT Config → Voice."
        else -> "Voice input failed (code $error). Try a different speech recognizer in KITT Config."
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
