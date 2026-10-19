package com.aiconsole.kittlauncher2.data

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock

/**
 * Safe mode for a launcher that keeps crashing at start-up. KITT is the home screen, so a crash
 * loop would leave the head unit stuck. After [SafeModeRules.CRASHES_TO_TRIP] crashes within a
 * minute of starting (in [SafeModeRules.SPAN_MS]), KITT starts without web pages, the scanner
 * overlay, the wake word and the offline voice, and shows a bar to send a report or go back.
 * Crash times are written synchronously from the crash handler, before the process dies.
 */
object SafeMode {

    private const val PREFS = "safe_mode"
    private const val KEY_CRASHES = "startup_crashes"
    private const val KEY_ON = "on"

    /** True for this whole process once start-up decided on safe mode. */
    @Volatile
    var active = false
        private set

    /** Decides at start-up (Application.onCreate), before anything heavy starts. */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val crashes = SafeModeRules.decode(prefs.getString(KEY_CRASHES, null))
        active = prefs.getBoolean(KEY_ON, false) || SafeModeRules.shouldTrip(crashes, System.currentTimeMillis())
        // Stays on across restarts until the driver taps "Back to normal".
        if (active) prefs.edit().putBoolean(KEY_ON, true).commit()
    }

    /** From the crash handler: a crash soon after start-up counts toward safe mode. */
    fun recordCrash(context: Context) {
        val upFor = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        if (upFor > SafeModeRules.STARTUP_WINDOW_MS) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val crashes = SafeModeRules.decode(prefs.getString(KEY_CRASHES, null))
        val now = System.currentTimeMillis()
        prefs.edit().putString(KEY_CRASHES, SafeModeRules.encode(SafeModeRules.recent(crashes + now, now))).commit()
    }

    /** A minute without crashing: earlier start-up crashes no longer count. */
    fun markStable(context: Context) {
        if (active) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CRASHES).apply()
    }

    /** "Back to normal": clears safe mode and restarts the launcher. */
    fun exit(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Process.killProcess(Process.myPid())
    }
}

/** Pure rules behind [SafeMode] (unit-tested). */
object SafeModeRules {
    /** Only crashes this soon after the process started count (a start-up crash loop). */
    const val STARTUP_WINDOW_MS = 60_000L
    const val CRASHES_TO_TRIP = 3
    /** ...within this long of each other. */
    const val SPAN_MS = 10 * 60_000L

    fun recent(crashes: List<Long>, now: Long): List<Long> = crashes.filter { now - it in 0..SPAN_MS }.takeLast(10)

    fun shouldTrip(crashes: List<Long>, now: Long): Boolean = recent(crashes, now).size >= CRASHES_TO_TRIP

    fun encode(crashes: List<Long>): String = crashes.joinToString(",")

    fun decode(text: String?): List<Long> = text.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
}
