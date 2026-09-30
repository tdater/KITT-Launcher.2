package com.aiconsole.knightriderlauncher.obd

/**
 * Rolling per-PID history for the live graphs: the last [windowMs] of readings (metric base
 * units), de-duplicated by timestamp. Pure Kotlin and thread-safe; fed from the readings flow.
 */
class PidHistory(private val windowMs: Long = 5 * 60_000L, private val maxPoints: Int = 3_000) {

    data class Point(val timestamp: Long, val value: Double)

    private val series = HashMap<String, ArrayDeque<Point>>()

    @Synchronized
    fun add(pidId: String, reading: PidReading) {
        val points = series.getOrPut(pidId) { ArrayDeque() }
        val last = points.lastOrNull()
        if (last != null && reading.timestamp <= last.timestamp) return
        points.addLast(Point(reading.timestamp, reading.value))
        val cutoff = reading.timestamp - windowMs
        while (points.size > maxPoints || (points.isNotEmpty() && points.first().timestamp < cutoff)) points.removeFirst()
    }

    /** Points for [pidId] no older than [sinceMs] ago relative to [now]. */
    @Synchronized
    fun points(pidId: String, now: Long, sinceMs: Long): List<Point> =
        series[pidId].orEmpty().filter { it.timestamp >= now - sinceMs }

    @Synchronized
    fun clear() = series.clear()
}
