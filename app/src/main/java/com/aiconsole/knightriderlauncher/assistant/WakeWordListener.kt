package com.aiconsole.knightriderlauncher.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * "Hey KITT": keeps a speech recognizer listening in short sessions while the launcher is in
 * front and idle, and fires [wakes] when it hears the phrase. Android has no public hotword API
 * for third-party apps, so this uses the regular recognizer: an on-device one (Android 12+) keeps
 * it offline; some recognizers chime each time a session starts. Main thread only.
 */
class WakeWordListener(
    context: Context,
    private val settings: KittSettings,
    private val voice: TextToSpeechHelper,
    scope: CoroutineScope
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var visible = false
    private var suspended = false
    private var speaking = false
    private var enabled = settings.wakeWord.value
    private var listening = false
    private var failures = 0

    private val _wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakes: SharedFlow<Unit> = _wakes.asSharedFlow()

    init {
        // A takeover while listening for "Hey KITT" would repeat forever: turn the wake word off.
        scope.launch(Dispatchers.Main) {
            MicGuard.stops.collect {
                stopAll()
                settings.wakeWord.set(false)
            }
        }
        scope.launch(Dispatchers.Main) {
            combine(settings.wakeWord.state, voice.speaking) { on, talking -> on to talking }.collect { (on, talking) ->
                enabled = on
                speaking = talking
                update()
            }
        }
    }

    /** The launcher is (not) in front. */
    fun setVisible(value: Boolean) {
        visible = value
        update()
    }

    /** Pause while the assistant or another overlay is using the microphone or the screen. */
    fun setSuspended(value: Boolean) {
        suspended = value
        update()
    }

    private fun shouldListen(): Boolean =
        enabled && visible && !suspended && !speaking && !MicGuard.autoListenBlocked() &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun update() {
        main.removeCallbacksAndMessages(null)
        if (shouldListen()) {
            if (!listening) startSession()
        } else {
            stopAll()
        }
    }

    private fun startSession() {
        if (!shouldListen()) return
        val target = SpeechRecognizers.resolve(appContext, settings.speechRecognizer.value)
        if (target == RecognizerTarget.Unavailable) {
            Log.w(TAG, "No safe speech recognizer; \"Hey KITT\" stays off")
            return
        }
        val active = recognizer ?: SpeechRecognizers.create(appContext, target).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        listening = true
        MicGuard.listeningStarted()
        try {
            active.startListening(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Wake word listener couldn't start", e)
            listening = false
            scheduleRestart(error = true)
        }
    }

    private fun stopAll() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun scheduleRestart(error: Boolean) {
        listening = false
        failures = if (error) failures + 1 else 0
        // Back off on repeated errors (e.g. no network recognizer) so the mic isn't hammered.
        val delayMs = if (error) (500L * failures).coerceAtMost(15_000L) else 150L
        main.postDelayed({ if (shouldListen()) startSession() }, delayMs)
    }

    private fun check(bundle: Bundle?): Boolean {
        val heard = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (heard.none(WakeWord::matches)) return false
        stopAll() // Free the microphone for the assistant.
        _wakes.tryEmit(Unit)
        return true
    }

    override fun onPartialResults(partialResults: Bundle?) {
        check(partialResults)
    }

    override fun onResults(results: Bundle?) {
        if (!check(results)) scheduleRestart(error = false)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(error = false)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopAll()
            else -> {
                stopAll()
                scheduleRestart(error = true)
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        failures = 0
    }

    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private companion object {
        const val TAG = "WakeWord"
    }
}
