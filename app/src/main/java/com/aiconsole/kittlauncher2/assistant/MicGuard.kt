package com.aiconsole.kittlauncher2.assistant

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** The head unit pulled the screen away right after KITT opened the microphone. */
data class MicTakeover(val atMillis: Long, val packageName: String?)

/**
 * Some head units (seen on a Phoenix Automotive Android 12 unit) jump to their radio/climate app
 * whenever another app opens the microphone, and fire the voice-button intent back at the
 * launcher, looping. This notices the launcher being covered within seconds of the mic starting,
 * stops listening at once, blocks automatic re-listening for a while, and (with Usage access)
 * names the app that took over so the driver can mute it.
 */
object MicGuard {

    private const val TAKEOVER_WINDOW_MS = 6_000L
    private const val COOLDOWN_MS = 2 * 60_000L
    private const val TAG = "MicGuard"
    private const val PREFS = "mic_guard"

    @Volatile
    private var listeningSince = 0L

    @Volatile
    private var blockedUntil = 0L

    private val _last = MutableStateFlow<MicTakeover?>(null)
    val last: StateFlow<MicTakeover?> = _last.asStateFlow()

    private val _stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when listening must stop because something took over the screen. */
    val stops: SharedFlow<Unit> = _stops.asSharedFlow()

    fun listeningStarted() {
        listeningSince = SystemClock.elapsedRealtime()
    }

    fun listeningEnded() {
        listeningSince = 0L
    }

    /** True for a while after a takeover: don't start listening on our own (voice button, wake word). */
    fun autoListenBlocked(): Boolean = SystemClock.elapsedRealtime() < blockedUntil

    /** Called when the launcher stops being visible. */
    fun launcherHidden(context: Context) {
        val since = listeningSince
        if (since == 0L || SystemClock.elapsedRealtime() - since > TAKEOVER_WINDOW_MS) return
        listeningSince = 0L
        blockedUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
        _stops.tryEmit(Unit)
        val culprit = foregroundApp(context)
        Log.w(TAG, "Launcher covered ${SystemClock.elapsedRealtime() - since} ms after the mic opened; foreground: ${culprit ?: "unknown (no usage access)"}")
        _last.value = MicTakeover(System.currentTimeMillis(), culprit)
        // Kept on disk so a problem report written after a restart still shows it.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("last_at", System.currentTimeMillis())
            putString("last_pkg", culprit)
        }
    }

    /** The last takeover, including ones from before the app restarted. */
    fun lastRecorded(context: Context): MicTakeover? {
        _last.value?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("last_at", 0L).takeIf { it > 0 } ?: return null
        return MicTakeover(at, prefs.getString("last_pkg", null))
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** The app that most recently came to the front (needs Usage access), excluding the launcher. */
    private fun foregroundApp(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 15_000, now + 1_000)
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val resumed = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            if (resumed && event.packageName != context.packageName) latest = event.packageName
        }
        return latest
    }

    /** ADB commands that stop [pkg] from reacting to the microphone, and clear the system assistant. */
    fun adbFix(pkg: String?): String {
        val target = pkg ?: "<package of the radio/climate app>"
        return listOf(
            "adb shell appops set $target RECORD_AUDIO ignore",
            "adb shell pm revoke $target android.permission.RECORD_AUDIO",
            "adb shell settings put secure assistant \"\"",
            "adb shell settings put secure voice_interaction_service \"\"",
            "adb shell settings put secure voice_recognition_service \"\""
        ).joinToString("\n")
    }

    /** Run right after the takeover happens to see which app is in front. */
    const val FIND_APP_COMMAND = "adb shell dumpsys activity activities | grep -E \"topResumedActivity|mResumedActivity\""
}
