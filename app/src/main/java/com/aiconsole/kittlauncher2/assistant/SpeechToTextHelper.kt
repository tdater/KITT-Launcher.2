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
class SpeechToTextHelper(context: Context, private val settings: KittSettings) : RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var boundTarget: RecognizerTarget? = null
    private val transcriber = GeminiTranscriber(settings)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var geminiJob: Job? = null

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
        val useGemini = when (settings.speechInput.value) {
            SpeechInput.Gemini -> true
            SpeechInput.Device -> false
            SpeechInput.Auto -> settings.geminiKey.isNotEmpty()
        }
        if (useGemini) {
            startGemini()
            return@onMain
        }
        val target = SpeechRecognizers.resolve(appContext, settings.speechRecognizer.value)
        if (target == RecognizerTarget.Unavailable) {
            _state.value = SpeechState.Error(
                "No safe speech recognizer here. Add a Gemini key in KITT Config to use KITT's own speech input, or pick a recognizer there."
            )
            return@onMain
        }
        if (target == RecognizerTarget.SystemDefault && !SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = SpeechState.Error("Voice input isn't available on this head unit. Type your question instead.")
            return@onMain
        }
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

    /** KITT's own path: record, then Gemini transcribes. No system recognizer involved. */
    private fun startGemini() {
        if (settings.geminiKey.isEmpty()) {
            _state.value = SpeechState.Error("KITT's speech input needs a Gemini key (KITT Config).")
            return
        }
        geminiJob?.cancel()
        _state.value = SpeechState.Listening()
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
                    transcriber.transcribe(pcm).let { if (it.isBlank()) SpeechState.Error("Didn't catch that.") else SpeechState.Result(it) }
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
