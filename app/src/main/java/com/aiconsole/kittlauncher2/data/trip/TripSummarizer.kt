package com.aiconsole.kittlauncher2.data.trip

import java.util.Locale
import kotlin.math.roundToInt

/** One drive, reconstructed from the 1 Hz trip log. Metric base units. */
data class TripSummary(
    val start: Long,
    val end: Long,
    val distanceKm: Double,
    val movingMs: Long,
    val idleMs: Long,
    val maxSpeedKmh: Int,
    /** Null when too few samples carried a fuel rate to trust the total. */
    val fuelLiters: Double?
) {
    val durationMs: Long get() = end - start
    val avgMovingSpeedKmh: Double get() = if (movingMs > 0) distanceKm / (movingMs / 3_600_000.0) else 0.0

    /** L/100 km, or null without fuel data or distance. */
    val litersPer100Km: Double? get() = fuelLiters?.takeIf { distanceKm >= 0.5 }?.let { it / distanceKm * 100.0 }

    /** US mpg, or null without fuel data or distance. */
    val mpg: Double? get() = litersPer100Km?.takeIf { it > 0 }?.let { 235.215 / it }
}

/** Pure trip reconstruction and formatting, unit-tested. */
object TripSummarizer {
    /** A logging gap longer than this (engine off, parked) starts a new trip. */
    const val TRIP_GAP_MS = 5 * 60_000L

    /** Each sample stands for at most this long, so a dropped link doesn't invent distance. */
    private const val MAX_SAMPLE_MS = 5_000L
    private const val MOVING_KMH = 3
    private const val MIN_TRIP_MS = 60_000L
    private const val FUEL_COVERAGE = 0.8

    fun trips(samples: List<TripSample>): List<TripSummary> {
        if (samples.isEmpty()) return emptyList()
        val out = mutableListOf<TripSummary>()
        var from = 0
        for (i in 1..samples.size) {
            if (i == samples.size || samples[i].timestamp - samples[i - 1].timestamp > TRIP_GAP_MS) {
                summarize(samples.subList(from, i))?.let(out::add)
                from = i
            }
        }
        return out
    }

    private fun summarize(run: List<TripSample>): TripSummary? {
        if (run.size < 2 || run.last().timestamp - run.first().timestamp < MIN_TRIP_MS) return null
        var distanceKm = 0.0
        var movingMs = 0L
        var idleMs = 0L
        var fuelL = 0.0
        var fuelMs = 0L
        var totalMs = 0L
        for (i in 1 until run.size) {
            val dt = (run[i].timestamp - run[i - 1].timestamp).coerceIn(0, MAX_SAMPLE_MS)
            val sample = run[i]
            totalMs += dt
            distanceKm += sample.speed * dt / 3_600_000.0
            if (sample.speed >= MOVING_KMH) movingMs += dt else idleMs += dt
            sample.fuelRate?.let {
                fuelL += it * dt / 3_600_000.0
                fuelMs += dt
            }
        }
        return TripSummary(
            start = run.first().timestamp,
            end = run.last().timestamp,
            distanceKm = distanceKm,
            movingMs = movingMs,
            idleMs = idleMs,
            maxSpeedKmh = run.maxOf { it.speed },
            fuelLiters = if (totalMs > 0 && fuelMs >= totalMs * FUEL_COVERAGE) fuelL * totalMs / fuelMs else null
        )
    }

    fun duration(ms: Long): String {
        val minutes = (ms / 60_000).toInt()
        return if (minutes >= 60) "${minutes / 60}h ${"%02d".format(Locale.US, minutes % 60)}m" else "${minutes}m"
    }

    /** Plain-English summary for KITT ("how was that drive?"). */
    fun describe(trip: TripSummary, metric: Boolean): String = buildString {
        val distance = if (metric) "%.1f km".format(Locale.US, trip.distanceKm) else "%.1f miles".format(Locale.US, trip.distanceKm * 0.621371)
        val avg = if (metric) "${trip.avgMovingSpeedKmh.roundToInt()} km/h" else "${(trip.avgMovingSpeedKmh * 0.621371).roundToInt()} mph"
        val top = if (metric) "${trip.maxSpeedKmh} km/h" else "${(trip.maxSpeedKmh * 0.621371).roundToInt()} mph"
        append("Distance $distance in ${duration(trip.durationMs)} (${duration(trip.idleMs)} idling). ")
        append("Average moving speed $avg, top speed $top.")
        val economy = if (metric) trip.litersPer100Km?.let { "%.1f L/100 km".format(Locale.US, it) } else trip.mpg?.let { "%.1f mpg".format(Locale.US, it) }
        economy?.let { append(" Fuel economy $it") }
        trip.fuelLiters?.let {
            append(if (metric) ", ${"%.1f".format(Locale.US, it)} L used." else ", ${"%.2f".format(Locale.US, it * 0.264172)} gal used.")
        }
    }
}
