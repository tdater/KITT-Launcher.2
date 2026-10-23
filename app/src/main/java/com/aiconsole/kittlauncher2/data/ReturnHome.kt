package com.aiconsole.kittlauncher2.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.aiconsole.kittlauncher2.EditionComponents
import com.aiconsole.kittlauncher2.ui.scanner.LauncherVisibility

/** When to pull the launcher back after the head unit covers it with its own source screen. */
object ReturnHomeRules {

    /** The cover must follow KITT's own audio or microphone use by no more than this. */
    const val AUDIO_WINDOW_MS = 8_000L

    /** Leaving through a tap (an app tile, the home button) is the driver's choice, never undone. */
    const val USER_LEAVE_WINDOW_MS = 2_000L

    /** No more than [MAX_RETURNS] pulls per [BURST_MS], so a unit that fights back can't loop us. */
    const val MAX_RETURNS = 3
    const val BURST_MS = 60_000L

    fun shouldReturn(
        enabled: Boolean,
        screenOn: Boolean,
        now: Long,
        lastAudioAt: Long,
        lastUserLeaveAt: Long,
        returns: List<Long>
    ): Boolean {
        if (!enabled || !screenOn) return false
        if (lastAudioAt <= 0 || now - lastAudioAt !in 0..AUDIO_WINDOW_MS) return false
        if (lastUserLeaveAt > 0 && now - lastUserLeaveAt in 0..USER_LEAVE_WINDOW_MS) return false
        return returns.count { now - it in 0..BURST_MS } < MAX_RETURNS
    }
}

/**
 * Some head units (seen on a Phoenix Automotive Android 12 unit) throw their stock radio screen
 * over the launcher whenever an app starts audio or opens the microphone. When the launcher is
 * covered that way within seconds of KITT's own audio, this brings it back. Opening an app from
 * the launcher is reported by [userLeaving] and is left alone. Starting an activity from the
 * background works because the launcher holds the "display over other apps" permission.
 */
object ReturnHome {

    private const val TAG = "ReturnHome"
    private const val DELAY_MS = 400L

    private val main = Handler(Looper.getMainLooper())
    private val returns = ArrayList<Long>()

    @Volatile
    private var lastAudioAt = 0L

    @Volatile
    private var lastUserLeaveAt = 0L

    /** KITT (or the media controls) just started audio or the microphone. */
    fun noteAudio() {
        lastAudioAt = SystemClock.elapsedRealtime()
    }

    /** The driver is leaving on purpose (MainActivity.onUserLeaveHint). */
    fun userLeaving() {
        lastUserLeaveAt = SystemClock.elapsedRealtime()
    }

    /** Called from MainActivity.onStop. */
    fun launcherHidden(context: Context, enabled: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive != false
        val go = synchronized(returns) {
            ReturnHomeRules.shouldReturn(enabled, screenOn, now, lastAudioAt, lastUserLeaveAt, returns).also {
                if (it) returns.add(now)
                returns.removeAll { t -> now - t > ReturnHomeRules.BURST_MS }
            }
        }
        if (!go) return
        val app = context.applicationContext
        main.postDelayed({
            if (LauncherVisibility.visible.value) return@postDelayed
            Log.w(TAG, "Launcher was covered right after KITT audio; bringing it back")
            try {
                app.startActivity(
                    Intent(app, EditionComponents.mainActivity)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not return to the launcher", e)
            }
        }, DELAY_MS)
    }
}
