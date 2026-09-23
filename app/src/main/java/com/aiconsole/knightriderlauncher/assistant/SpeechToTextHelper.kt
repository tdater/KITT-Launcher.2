package com.aiconsole.knightriderlauncher.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
class SpeechToTextHelper(context: Context) : RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    fun start() = onMain {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = SpeechState.Error("Voice input isn't available on this head unit. Type your question instead.")
            return@onMain
        }
        val active = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        _state.value = SpeechState.Listening()
        active.startListening(intent)
    }

    /** Stop capturing and deliver whatever was heard. */
    fun stop() = onMain { recognizer?.stopListening() }

    /** Abort without a result. */
    fun cancel() = onMain {
        recognizer?.cancel()
        _state.value = SpeechState.Idle
    }

    fun reset() {
        _state.value = SpeechState.Idle
    }

    fun release() = onMain {
        recognizer?.destroy()
        recognizer = null
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
        _state.value = SpeechState.Processing
    }

    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        _state.value = if (text.isNullOrBlank()) SpeechState.Error("Didn't catch that.") else SpeechState.Result(text)
    }

    override fun onError(error: Int) {
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
        else -> "Voice input failed (code $error)."
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
