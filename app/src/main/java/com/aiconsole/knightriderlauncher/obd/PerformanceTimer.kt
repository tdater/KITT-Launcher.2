package com.aiconsole.knightriderlauncher.obd

/** Which run the timer measures. Speeds are km/h, distances metres. */
enum class PerformanceRun(val label: String, val metricLabel: String) {
    ZeroToSixty("0-60 mph", "0-100 km/h"),
    QuarterMile("1/4 mile", "402 m"),
    SixtyToZero("60-0 braking", "100-0 braking");

    fun targetKmh(metric: Boolean): Double = if (metric) 100.0 else 96.5606
}

data class PerformanceResult(
    val run: PerformanceRun,
    val seconds: Double,
    val distanceM: Double,
    val trapKmh: Double,
    val at: Long
)

/**
 * A small state machine fed with (timestamp, speed) samples. Accelerations arm while stopped
 * and start the moment the car moves; braking arms above the start speed and starts as the
 * speed drops through it. Distance is integrated from speed (trapezoids). OBD speed is whole
 * km/h at the poll rate, so results are indicative (roughly ±0.2 s at 10 Hz).
 */
class PerformanceTimer(private val run: PerformanceRun, private val metric: Boolean) {

    enum class Phase { WaitingToArm, Armed, Running, Done }

    var phase = Phase.WaitingToArm
        private set
    var result: PerformanceResult? = null
        private set

    /** Elapsed time and distance of the run in progress. */
    var elapsedS = 0.0
        private set
    var distanceM = 0.0
        private set

    private var startT = 0L
    private var lastT = 0L
    private var lastKmh = 0.0

    fun feed(timestamp: Long, kmh: Double) {
        val target = run.targetKmh(metric)
        when (phase) {
            Phase.WaitingToArm -> {
                val ready = if (run == PerformanceRun.SixtyToZero) kmh > target + 3 else kmh < 0.5
                if (ready) phase = Phase.Armed
            }
            Phase.Armed -> {
                val go = if (run == PerformanceRun.SixtyToZero) kmh <= target else kmh >= 0.5
                if (go) {
                    phase = Phase.Running
                    startT = timestamp
                    lastT = timestamp
                    lastKmh = kmh
                    distanceM = 0.0
                    elapsedS = 0.0
                } else if (run != PerformanceRun.SixtyToZero && kmh >= 0.5) {
                    phase = Phase.WaitingToArm
                }
            }
            Phase.Running -> {
                val dt = (timestamp - lastT) / 1000.0
                if (dt <= 0) return
                distanceM += (lastKmh + kmh) / 2 / 3.6 * dt
                elapsedS = (timestamp - startT) / 1000.0
                lastT = timestamp
                lastKmh = kmh
                val finished = when (run) {
                    PerformanceRun.ZeroToSixty -> kmh >= target
                    PerformanceRun.QuarterMile -> distanceM >= QUARTER_MILE_M
                    PerformanceRun.SixtyToZero -> kmh < 0.5
                }
                // An acceleration run that stops before finishing is abandoned.
                val aborted = run != PerformanceRun.SixtyToZero && kmh < 0.5 && elapsedS > 1
                when {
                    finished -> {
                        phase = Phase.Done
                        result = PerformanceResult(run, elapsedS, distanceM, kmh, timestamp)
                    }
                    aborted -> phase = Phase.Armed
                }
            }
            Phase.Done -> Unit
        }
    }

    companion object {
        const val QUARTER_MILE_M = 402.336
    }
}
