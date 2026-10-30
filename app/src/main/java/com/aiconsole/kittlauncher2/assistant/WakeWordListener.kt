package com.aiconsole.kittlauncher2.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.aiconsole.kittlauncher2.browser.WebMedia
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Pure rules for [WakeWordListener] (unit-tested). */
object WakeWordRules {
    /**
     * Listen only when everything allows it. Never while music or any other media is playing:
     * the recognizer can't hear "Hey KITT" over it anyway, and restarting a session every few
     * seconds made head units chime, duck or interrupt the music over and over.
     */
    fun shouldListen(
        enabled: Boolean,
        visible: Boolean,
        suspended: Boolean,
        kittSpeaking: Boolean,
        micBlocked: Boolean,
        mediaPlaying: Boolean,
        micPermission: Boolean
    ): Boolean = enabled && visible && !suspended && !kittSpeaking && !micBlocked && !mediaPlaying && micPermission

    /** Pause before the next session: a short gap after a quiet one, growing back-off after errors. */
    fun restartDelayMs(error: Boolean, failures: Int): Long =
        if (error) (500L * failures).coerceIn(500L, 15_000L) else QUIET_GAP_MS

    /**
     * Remaining startup or post-speech cooldown in ms before the system [SpeechRecognizer] may
     * start listening. The offline spotter ([useOffline] == true) never chimes or takes audio focus,
     * so it needs no startup or post-speech delay.
     */
    fun cooldownRemainingMs(nowElapsedMs: Long, createdAtMs: Long, lastSpokeAtMs: Long, useOffline: Boolean): Long {
        if (useOffline) return 0L
        val sinceStart = nowElapsedMs - createdAtMs
        if (sinceStart < STARTUP_GRACE_MS) return (STARTUP_GRACE_MS - sinceStart).coerceAtLeast(250L)
        if (lastSpokeAtMs > 0L) {
            val sinceSpoke = nowElapsedMs - lastSpokeAtMs
            if (sinceSpoke < POST_SPEECH_COOLDOWN_MS) return (POST_SPEECH_COOLDOWN_MS - sinceSpoke).coerceAtLeast(250L)
        }
        return 0L
    }

    /**
     * Whether to immediately start another session after a quiet timeout. The offline spotter runs
     * continuously; Android's system [SpeechRecognizer] chimes on every start, so it caps consecutive
     * silent sessions at [MAX_CHIME_SESSIONS] until the next interaction or state change.
     */
    fun shouldContinueAfterQuietSession(useOffline: Boolean, quietSessions: Int): Boolean =
        useOffline || quietSessions < MAX_CHIME_SESSIONS

    /** How often to look again while media plays (no callback says when it stops). */
    const val MEDIA_RECHECK_MS = 5_000L
    const val STARTUP_GRACE_MS = 3_500L
    const val POST_SPEECH_COOLDOWN_MS = 1_500L
    const val MAX_CHIME_SESSIONS = 1
    private const val QUIET_GAP_MS = 1_000L
}

/**
 * "Hey KITT": keeps a speech recognizer listening in short sessions while the launcher is in
 * front and idle, and fires [wakes] when it hears the phrase. Android has no public hotword API
 * for third-party apps, so this uses the regular recognizer: an on-device one (Android 12+) keeps
 * it offline; some recognizers chime each time a session starts. It stands down while any app is
 * playing media (see [WakeWordRules.shouldListen]). Main thread only.
 */
class WakeWordListener(
    context: Context,
    private val settings: KittSettings,
    private val voice: TextToSpeechHelper,
    /** The offline spotter, used instead of the recognizer when chosen and downloaded. */
    private val offline: OfflineWake,
    scope: CoroutineScope
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var recognizer: SpeechRecognizer? = null
    private var visible = false
    private var suspended = false
    private var speaking = false
    private var enabled = settings.wakeWord.value
    private var listening = false
    private var failures = 0
    private var quietSessions = 0
    private val createdAtMs = SystemClock.elapsedRealtime()
    private var lastSpokeAtMs = 0L

    private val _wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakes: SharedFlow<Unit> = _wakes.asSharedFlow()

    /** "KITT, pause music" heard offline: the command text for VoiceCommands. */
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    private var offlineChosen = settings.wakeOffline.value
    private var offlineInstalled = offline.installed.value

    private fun useOffline(): Boolean = offlineChosen && offline.installed.value && offline.unavailable == null

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
                val wasSpeaking = speaking
                enabled = on
                speaking = talking
                if (wasSpeaking && !talking) {
                    lastSpokeAtMs = SystemClock.elapsedRealtime()
                    quietSessions = 0
                } else if (talking) {
                    lastSpokeAtMs = SystemClock.elapsedRealtime()
                }
                update()
            }
        }
        scope.launch(Dispatchers.Main) {
            combine(settings.wakeOffline.state, offline.installed) { chosen, isInstalled -> chosen to isInstalled }
                .collect { (chosen, isInstalled) ->
                    if (offlineChosen == chosen && offlineInstalled == isInstalled) return@collect
                    // Switching engines or install state changed: stop the current one, then start the other.
                    stopAll()
                    offlineChosen = chosen
                    offlineInstalled = isInstalled
                    quietSessions = 0
                    update()
                }
        }
    }

    /** The launcher is (not) in front. */
    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        quietSessions = 0
        update()
    }

    /** Pause while the assistant or another overlay is using the microphone or the screen. */
    fun setSuspended(value: Boolean) {
        if (suspended == value) return
        suspended = value
        if (!value) quietSessions = 0
        update()
    }

    // A browser page counts while it's paused for a moment too (see WebMedia.busy).
    private fun mediaPlaying(): Boolean = audio?.isMusicActive == true || WebMedia.busy

    private fun shouldListen(): Boolean = WakeWordRules.shouldListen(
        enabled = enabled,
        visible = visible,
        suspended = suspended,
        kittSpeaking = speaking,
        micBlocked = MicGuard.autoListenBlocked(),
        // The offline spotter needs no audio focus and never chimes: it keeps listening over music.
        mediaPlaying = !useOffline() && mediaPlaying(),
        micPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )

    private fun update() {
        main.removeCallbacksAndMessages(null)
        if (shouldListen()) {
            val waitMs = WakeWordRules.cooldownRemainingMs(SystemClock.elapsedRealtime(), createdAtMs, lastSpokeAtMs, useOffline())
            if (waitMs > 0L) {
                main.postDelayed({ update() }, waitMs)
                return
            }
            if (!WakeWordRules.shouldContinueAfterQuietSession(useOffline(), quietSessions)) return
            if (!listening) startSession()
        } else {
            stopAll()
            waitForMediaToStop()
        }
    }

    /** Media is the only blocker nothing tells us about: look again every few seconds until it stops. */
    private fun waitForMediaToStop() {
        if (enabled && visible && !suspended && mediaPlaying()) {
            main.postDelayed({ update() }, WakeWordRules.MEDIA_RECHECK_MS)
        }
    }

    private fun startSession() {
        if (!shouldListen()) return
        if (useOffline()) {
            listening = offline.start(::heardOffline) { scheduleRestart(error = true) }
            if (listening) MicGuard.listeningStarted()
            return
        }
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
        offline.stop()
        recognizer?.destroy()
        recognizer = null
    }

    private fun heardOffline(heard: OfflineHeard) {
        when (heard) {
            OfflineHeard.Wake -> {
                stopAll() // Free the microphone for the assistant.
                _wakes.tryEmit(Unit)
            }
            is OfflineHeard.Command -> _commands.tryEmit(heard.text)
        }
    }

    private fun scheduleRestart(error: Boolean) {
        listening = false
        if (error) {
            failures += 1
        } else {
            failures = 0
            quietSessions += 1
            if (!WakeWordRules.shouldContinueAfterQuietSession(useOffline(), quietSessions)) {
                stopAll()
                return
            }
        }
        // Back off on repeated errors (e.g. no network recognizer) so the mic isn't hammered.
        main.postDelayed({ update() }, WakeWordRules.restartDelayMs(error, failures))
    }

    private fun check(bundle: Bundle?): Boolean {
        val heard = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (heard.none(WakeWord::matches)) return false
        quietSessions = 0
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
