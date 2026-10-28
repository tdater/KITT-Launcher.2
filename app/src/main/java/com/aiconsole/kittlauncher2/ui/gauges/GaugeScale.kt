package com.aiconsole.kittlauncher2.ui.gauges

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Highest value each gauge has seen this drive. A gap of [NEW_DRIVE_MS] without readings (ignition
 * off) starts a new drive, so the mark doesn't carry yesterday's top speed forever.
 */
class PeakHold {
    private class Peak(var value: Double, var lastSeen: Long)

    private val peaks = HashMap<String, Peak>()

    @Synchronized
    fun update(id: String, value: Double, timestamp: Long): Double {
        val peak = peaks[id]
        if (peak == null || timestamp - peak.lastSeen > NEW_DRIVE_MS) {
            peaks[id] = Peak(value, timestamp)
            return value
        }
        if (value > peak.value) peak.value = value
        if (timestamp > peak.lastSeen) peak.lastSeen = timestamp
        return peak.value
    }

    @Synchronized
    fun reset(id: String) {
        peaks.remove(id)
    }

    companion object {
        const val NEW_DRIVE_MS = 120_000L

        /** Shared by every gauge tile, so the Gauges page and Diagnostics show the same peaks. */
        val shared = PeakHold()
    }
}

/** Scale maths shared by the drawn gauges. Plain Kotlin so it is unit-tested. */
object GaugeScale {

    /** Where [value] sits between [min] and [max], clamped to 0..1; 0 when unknown. */
    fun fraction(value: Double?, min: Double, max: Double): Float {
        if (value == null || value.isNaN() || max <= min) return 0f
        return ((value - min) / (max - min)).toFloat().coerceIn(0f, 1f)
    }

    /** A 1-2-5 step that splits [span] into roughly [target] intervals. */
    fun niceStep(span: Double, target: Int): Double {
        if (span <= 0 || target <= 0 || span.isNaN() || span.isInfinite()) return 1.0
        val raw = span / target
        val magnitude = 10.0.pow(floor(log10(raw)))
        val normalized = raw / magnitude - 1e-9
        val nice = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 5.0 -> 5.0
            else -> 10.0
        }
        return nice * magnitude
    }

    /** Multiples of [step] from [from] to [to] inclusive (capped at 200 ticks). */
    fun ticks(from: Double, to: Double, step: Double): List<Double> {
        if (step <= 0 || to < from || step.isNaN()) return emptyList()
        val epsilon = step * 1e-6
        val first = ceil((from - epsilon) / step) * step
        val out = ArrayList<Double>()
        var v = first
        while (v <= to + epsilon && out.size < 200) {
            out += if (abs(v) < epsilon) 0.0 else v
            v = first + out.size * step
        }
        return out
    }

    /** True when [value] lands on a multiple of [step]. */
    fun isMultiple(value: Double, step: Double): Boolean {
        if (step <= 0) return false
        val q = value / step
        return abs(q - q.roundToLong()) < 1e-6
    }

    /** Large scales (RPM) are labelled in thousands, like a tachometer. */
    fun labelDivisor(max: Double): Double = if (abs(max) >= 2000) 1000.0 else 1.0

    fun label(value: Double, divisor: Double = 1.0): String {
        val v = value / divisor
        return if (abs(v - v.roundToLong()) < 1e-6) v.roundToLong().toString()
        else String.format(Locale.US, "%.1f", v)
    }

    /** Needle angle in degrees (0 = 3 o'clock, clockwise) across a [sweep] starting at [start]. */
    fun needleAngle(fraction: Float, start: Float = 135f, sweep: Float = 270f): Float =
        start + sweep * fraction.coerceIn(0f, 1f)

    /** Heights of stair-step bars, rising evenly from [floor] to full height. */
    fun skylineHeights(bars: Int, floor: Float = 0.25f): List<Float> = when {
        bars <= 0 -> emptyList()
        bars == 1 -> listOf(1f)
        else -> List(bars) { floor + (1f - floor) * it / (bars - 1f) }
    }

    /** How many of [count] segments are lit at [fraction]. */
    fun lit(fraction: Float, count: Int): Int = (fraction.coerceIn(0f, 1f) * count).roundToInt()

    /**
     * Characters for a row of display cells: '.' joins the previous character as its decimal
     * point, and the row is right-aligned to [cells] with blanks.
     */
    fun cells(text: String, cells: Int): List<Pair<Char, Boolean>> {
        val out = mutableListOf<Pair<Char, Boolean>>()
        text.forEach { ch ->
            if (ch == '.' && out.isNotEmpty() && !out.last().second) out[out.lastIndex] = out.last().first to true
            else out += ch to false
        }
        while (out.size < cells) out.add(0, ' ' to false)
        return out
    }
}
