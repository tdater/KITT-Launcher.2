package com.aiconsole.kittlauncher2.obd

/** Battery and charging assessment from voltage and RPM history. */
data class BatteryReport(
    /** Latest voltage with the engine off, and the charge it suggests. */
    val restingVolts: Double?,
    val restingState: String?,
    /** Lowest voltage while cranking at the last start. */
    val crankingMinVolts: Double?,
    val crankingState: String?,
    /** Latest voltage with the engine running. */
    val chargingVolts: Double?,
    val chargingState: String?
) {
    val warnings: List<String>
        get() = listOfNotNull(
            restingState?.takeIf { restingVolts != null && restingVolts < 12.2 },
            crankingState?.takeIf { crankingMinVolts != null && crankingMinVolts < 9.6 },
            chargingState?.takeIf { chargingVolts != null && (chargingVolts < BatteryHealth.NOT_CHARGING_V || chargingVolts > 15.0) }
        )
}

/** Plain-Kotlin rules of thumb for a 12 V lead-acid / AGM starter battery. */
object BatteryHealth {

    private const val RUNNING_RPM = 400.0

    /**
     * Smart-charging alternators (GM, Ford, most cars since ~2014) idle at 12.2-12.8 V once the
     * battery is full, so only below a healthy resting battery is the engine running on it.
     * Same limit as KittAlerts' spoken charging warning.
     */
    const val NOT_CHARGING_V = 12.0

    fun restingState(v: Double): String = when {
        v >= 12.6 -> "Fully charged"
        v >= 12.4 -> "About 75% charged"
        v >= 12.2 -> "About 50% charged"
        v >= 12.0 -> "About 25% charged: charge it soon"
        else -> "Discharged: charge or test the battery"
    }

    fun crankingState(v: Double): String = when {
        v >= 10.0 -> "Strong start"
        v >= 9.6 -> "Acceptable start"
        else -> "Weak: the battery sags hard when cranking; have it load-tested"
    }

    fun chargingState(v: Double): String = when {
        v > 15.0 -> "Overcharging: check the alternator's regulator"
        v >= 13.7 -> "Charging normally"
        v >= 13.2 -> "Charging on the low side (normal for smart alternators at cruise)"
        v >= NOT_CHARGING_V -> "Charging lightly (normal for smart alternators with a full battery)"
        else -> "Not charging properly: check the alternator and belt"
    }

    /**
     * [voltage] and [rpm] are time-ordered (timestamp, value) samples. Cranking is found as the
     * lowest voltage in the few seconds before RPM first rises past running speed.
     */
    fun analyze(voltage: List<PidHistory.Point>, rpm: List<PidHistory.Point>): BatteryReport {
        fun rpmAt(t: Long): Double? = rpm.lastOrNull { it.timestamp <= t }?.value
        val running = { p: PidHistory.Point -> (rpmAt(p.timestamp) ?: 0.0) >= RUNNING_RPM }
        val resting = voltage.lastOrNull { !running(it) && rpmAt(it.timestamp) != null }?.value
            ?: voltage.lastOrNull { rpm.isEmpty() }?.value
        val charging = voltage.lastOrNull(running)?.value

        val start = rpm.zipWithNext().lastOrNull { (a, b) -> a.value < RUNNING_RPM && b.value >= RUNNING_RPM }?.second?.timestamp
        val cranking = start?.let { t -> voltage.filter { it.timestamp in (t - 5_000)..t }.minOfOrNull { it.value } }

        return BatteryReport(
            restingVolts = resting,
            restingState = resting?.let(::restingState),
            crankingMinVolts = cranking,
            crankingState = cranking?.let(::crankingState),
            chargingVolts = charging,
            chargingState = charging?.let(::chargingState)
        )
    }
}
