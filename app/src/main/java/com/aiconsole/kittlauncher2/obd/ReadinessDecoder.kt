package com.aiconsole.kittlauncher2.obd

/** One emissions self-test the ECU supports, and whether it has run since codes were last cleared. */
data class ReadinessMonitor(val name: String, val complete: Boolean)

data class Readiness(
    val milOn: Boolean,
    val dtcCount: Int,
    /** Diesel monitor set rather than gasoline. */
    val compressionIgnition: Boolean,
    val monitors: List<ReadinessMonitor>
) {
    val incomplete: List<ReadinessMonitor> get() = monitors.filterNot { it.complete }

    val verdict: Verdict
        get() = when {
            milOn -> Verdict.NotReady
            incomplete.isEmpty() -> Verdict.Ready
            incomplete.size == 1 -> Verdict.LikelyReady
            else -> Verdict.NotReady
        }

    enum class Verdict { Ready, LikelyReady, NotReady }
}

/**
 * Decodes Mode 01 PID 01 (monitor status since DTCs cleared, SAE J1979):
 * A = MIL + DTC count; B = common monitors (bits 0-2 supported, 4-6 incomplete) and the
 * ignition type (bit 3); C/D = engine-specific monitors supported / incomplete.
 */
object ReadinessDecoder {

    private val COMMON = listOf("Misfire", "Fuel system", "Comprehensive components")

    private val SPARK = listOf(
        "Catalyst", "Heated catalyst", "Evaporative system", "Secondary air",
        "A/C refrigerant", "Oxygen sensor", "Oxygen sensor heater", "EGR / VVT"
    )

    private val COMPRESSION = listOf(
        "NMHC catalyst", "NOx / SCR aftertreatment", null, "Boost pressure",
        null, "Exhaust gas sensor", "Particulate filter", "EGR / VVT"
    )

    fun decode(data: ByteArray): Readiness? {
        if (data.size < 4) return null
        val a = data[0].toInt() and 0xFF
        val b = data[1].toInt() and 0xFF
        val c = data[2].toInt() and 0xFF
        val d = data[3].toInt() and 0xFF
        val compression = b and 0x08 != 0

        val monitors = mutableListOf<ReadinessMonitor>()
        COMMON.forEachIndexed { bit, name ->
            if (b and (1 shl bit) != 0) monitors += ReadinessMonitor(name, complete = b and (1 shl (bit + 4)) == 0)
        }
        (if (compression) COMPRESSION else SPARK).forEachIndexed { bit, name ->
            if (name != null && c and (1 shl bit) != 0) {
                monitors += ReadinessMonitor(name, complete = d and (1 shl bit) == 0)
            }
        }
        return Readiness(
            milOn = a and 0x80 != 0,
            dtcCount = a and 0x7F,
            compressionIgnition = compression,
            monitors = monitors
        )
    }
}
