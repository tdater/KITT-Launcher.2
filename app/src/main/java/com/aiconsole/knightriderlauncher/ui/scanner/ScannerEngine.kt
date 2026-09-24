package com.aiconsole.knightriderlauncher.ui.scanner

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp

/**
 * The KITT scanner as a light simulation rather than a canned animation: a head sweeps
 * back and forth with easing at each end, and every lamp it passes keeps glowing and
 * decays exponentially, like the incandescent lamps on the car. The trail therefore
 * looks right at the turnaround too. Pure Kotlin; shared by Compose and the overlay View.
 */
class ScannerEngine(
    val lamps: Int = 8,
    private val sweepMs: Double = 1_050.0,
    private val decayMs: Double = 260.0
) {
    private val levels = FloatArray(lamps)
    private var lastNanos = 0L
    private var elapsedMs = 0.0

    /** Speeds the sweep up (e.g. 1.8 while an alert is active). */
    var speed: Float = 1f

    companion object {
        /**
         * Sweep speed for the engine state: idle-calm at low revs, up to ~2x near redline,
         * and never slower than the alert pace while an alert is up.
         */
        fun speedFor(alert: Boolean, rpmFraction: Float): Float {
            val revs = 0.8f + rpmFraction.coerceIn(0f, 1f) * 1.2f
            return if (alert) maxOf(1.8f, revs) else revs
        }

        /** Brightness multiplier while KITT talks: a quick 0.55..1 throb, like the voice box. */
        fun voicePulse(nowNanos: Long): Float {
            val t = (nowNanos / 1_000_000L % 360L) / 360.0
            return (0.55 + 0.45 * abs(kotlin.math.sin(t * 2 * PI))).toFloat()
        }
    }

    /** Advances to [nowNanos] and returns lamp brightness 0..1. The array is reused. */
    fun step(nowNanos: Long): FloatArray {
        val dt = if (lastNanos == 0L) 0.0 else ((nowNanos - lastNanos) / 1_000_000.0).coerceIn(0.0, 100.0)
        lastNanos = nowNanos
        elapsedMs += dt * speed

        val phase = (elapsedMs % (2 * sweepMs)) / sweepMs // 0..2: right, then left
        val t = if (phase < 1.0) phase else 2.0 - phase
        val head = ((1 - cos(PI * t)) / 2 * (lamps - 1)).toFloat()

        val decay = if (dt == 0.0) 1f else exp(-dt / decayMs).toFloat()
        for (i in 0 until lamps) {
            val lit = (1f - abs(i - head)).coerceIn(0f, 1f)
            levels[i] = maxOf(levels[i] * decay, lit)
        }
        return levels
    }
}
