package com.aiconsole.kittlauncher2.assistant

/**
 * The last few things KITT's voice did (which engine spoke, model load and synthesis times,
 * playback results, fallbacks), for the problem report. Logcat on head units is too short-lived
 * to show why a voice stayed silent.
 */
object VoiceLog {

    class Entry(val timestamp: Long, val message: String)

    private const val MAX = 60
    private val entries = ArrayDeque<Entry>()

    fun add(message: String) {
        synchronized(entries) {
            entries.addLast(Entry(System.currentTimeMillis(), message))
            while (entries.size > MAX) entries.removeFirst()
        }
    }

    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    /** "2.4 s of audio in 6.1 s (2.5x slower than real time)": how a voice keeps up on this unit. */
    fun speed(audioMs: Long, tookMs: Long): String {
        val ratio = if (audioMs > 0) tookMs.toDouble() / audioMs else 0.0
        val pace = if (ratio > 1.0) "%.1fx slower than real time".format(java.util.Locale.US, ratio) else "faster than real time"
        return "%.1f s of audio in %.1f s (%s)".format(java.util.Locale.US, audioMs / 1000.0, tookMs / 1000.0, pace)
    }
}
