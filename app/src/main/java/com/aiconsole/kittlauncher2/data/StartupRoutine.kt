package com.aiconsole.kittlauncher2.data

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import com.aiconsole.kittlauncher2.media.MediaBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Start-up routine: when the launcher first starts, or the screen comes back after a long
 * sleep (head units usually sleep on ignition off rather than power down), optionally open a
 * chosen app and resume audio.
 */
class StartupRoutine(
    context: Context,
    private val settings: KittSettings,
    private val media: MediaBridge,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private var ranThisProcess = false
    private var screenOffAt: Long? = null
    private var pendingWake = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenOffAt = SystemClock.elapsedRealtime()
                Intent.ACTION_SCREEN_ON -> {
                    val off = screenOffAt?.let { SystemClock.elapsedRealtime() - it }
                    screenOffAt = null
                    if (StartupRule.shouldRun(false, off)) pendingWake = true
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Called when the launcher becomes visible; runs the routine if it's due. */
    fun onLauncherShown() {
        val due = StartupRule.shouldRun(!ranThisProcess, null) || pendingWake
        ranThisProcess = true
        pendingWake = false
        if (!due) return
        val appKey = settings.startupApp.value
        val resume = settings.startupResumeMedia.value
        if (appKey.isEmpty() && !resume) return
        scope.launch(Dispatchers.Main) {
            delay(START_DELAY_MS) // Let the greeting and OBD connection get going first.
            if (resume) resumeAudio()
            if (appKey.isNotEmpty()) openApp(appKey)
        }
    }

    private fun resumeAudio() {
        val now = media.nowPlaying.value
        if (now != null) {
            if (!now.playing) media.playPause()
            return
        }
        val audio = appContext.getSystemService(AudioManager::class.java) ?: return
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
    }

    private fun openApp(key: String) {
        val pkg = key.substringBefore('/')
        val activity = key.substringAfter('/', "")
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(pkg, activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }

    private companion object {
        const val START_DELAY_MS = 4_000L
    }
}
