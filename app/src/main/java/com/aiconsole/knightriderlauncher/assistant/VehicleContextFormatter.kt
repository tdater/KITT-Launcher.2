package com.aiconsole.knightriderlauncher.assistant

import com.aiconsole.knightriderlauncher.data.trip.MetricBaseline
import com.aiconsole.knightriderlauncher.data.trip.VehicleTelemetry
import kotlin.math.roundToInt

/** Snapshot of what the OBD link knows right now, already formatted for display units. */
data class VehicleContext(
    val linkStatus: String,
    val vehicle: String?,
    val liveReadings: List<String>,
    val storedCodes: List<String>,
    val pendingCodes: List<String>,
    /** Code that stored the freeze frame, and its readings formatted like [liveReadings]. */
    val freezeFrameCode: String? = null,
    val freezeFrame: List<String> = emptyList(),
    /** Emissions monitor summary from the last code scan, e.g. "2 incomplete: Catalyst, Evaporative system". */
    val readiness: String? = null
)

/** Turns live OBD data and the Room baseline into compact prompt context. */
object VehicleContextFormatter {

    private const val KMH_TO_MPH = 0.621371
    private const val COOLANT_MARGIN_C = 10
    private const val OVERHEAT_C = 110

    fun vehicleContext(context: VehicleContext?, baselineSummary: String): String = buildString {
        appendLine("VEHICLE: ${context?.vehicle ?: "unknown"}")
        appendLine("OBD LINK: ${context?.linkStatus ?: "not connected"}")
        appendLine("LIVE DATA:")
        val readings = context?.liveReadings.orEmpty()
        if (readings.isEmpty()) appendLine("- none available") else readings.forEach { appendLine("- $it") }
        val stored = context?.storedCodes.orEmpty()
        val pending = context?.pendingCodes.orEmpty()
        appendLine(
            "FAULT CODES: " + if (stored.isEmpty() && pending.isEmpty()) {
                "none reported"
            } else {
                listOfNotNull(
                    stored.takeIf { it.isNotEmpty() }?.let { "stored ${it.joinToString()}" },
                    pending.takeIf { it.isNotEmpty() }?.let { "pending ${it.joinToString()}" }
                ).joinToString("; ")
            }
        )
        val frameCode = context?.freezeFrameCode
        val frame = context?.freezeFrame.orEmpty()
        if (frameCode != null && frame.isNotEmpty()) {
            appendLine("FREEZE FRAME (conditions when $frameCode set):")
            frame.forEach { appendLine("- $it") }
        }
        context?.readiness?.let { appendLine("EMISSIONS READINESS (last scan): $it") }
        appendLine("HISTORICAL BASELINE (last 30 days):")
        append(baselineSummary)
    }

    fun baseline(baseline: MetricBaseline, live: VehicleTelemetry?): String {
        if (baseline.sampleCount == 0) return "No trip history logged yet."
        val lines = buildList {
            add("Samples logged: ${baseline.sampleCount}")
            baseline.avgCoolant?.let { avg ->
                add("Avg coolant: ${avg.roundToInt()}°C (peak ${baseline.maxCoolant ?: "?"}°C)")
            }
            baseline.avgRpm?.let { add("Avg RPM: ${it.roundToInt()}") }
            baseline.avgSpeed?.let { add("Avg speed: ${toMph(it.roundToInt())} mph") }
            baseline.avgThrottle?.let { add("Avg throttle: ${it.roundToInt()}%") }
            val found = anomalies(baseline, live)
            add("Anomalies vs baseline: " + if (found.isEmpty()) "none detected" else found.joinToString("; "))
        }
        return lines.joinToString("\n")
    }

    /** Live readings that deviate meaningfully from this vehicle's own history. */
    fun anomalies(baseline: MetricBaseline, live: VehicleTelemetry?): List<String> {
        val r = live?.reading ?: return emptyList()
        return buildList {
            if (r.coolantTemp >= OVERHEAT_C) {
                add("coolant ${r.coolantTemp}°C is at overheating level")
            } else {
                baseline.avgCoolant?.let { avg ->
                    val delta = r.coolantTemp - avg
                    if (delta > COOLANT_MARGIN_C) add("coolant ${r.coolantTemp}°C is ${delta.roundToInt()}°C above 30-day average")
                }
            }
            if (live.faultCodes.isNotEmpty()) add("active fault codes ${live.faultCodes.joinToString()}")
        }
    }

    private fun toMph(kmh: Int): Int = (kmh * KMH_TO_MPH).roundToInt()
}
