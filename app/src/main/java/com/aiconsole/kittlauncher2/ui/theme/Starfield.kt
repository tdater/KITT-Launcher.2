package com.aiconsole.kittlauncher2.ui.theme

import kotlin.math.PI
import kotlin.random.Random

/**
 * The Bridge cockpit's viewport: stars drifting out from the centre, as seen through a ship's
 * bridge window. Pure Kotlin (no Android types) so it is unit-tested.
 *
 * Positions are polar around the centre: [angle] in radians, [radius] from 0 (centre) to 1 (the
 * corner), [depth] 0.3..1 (nearer stars move faster and draw bigger). [warm] stars take the
 * theme's warm colour, the rest its text colour.
 */
class Starfield(count: Int = DEFAULT_COUNT, private val random: Random = Random.Default) {
    val angle = FloatArray(count)
    val radius = FloatArray(count)
    val depth = FloatArray(count)
    val warm = BooleanArray(count)

    val size: Int get() = angle.size

    init {
        for (i in 0 until count) spawn(i, anywhere = true)
    }

    /**
     * Moves every star outward for [dtMs] milliseconds at [speedFraction] (0 = parked, 1 = fast).
     * Parked means standing still, so the launcher draws nothing new while idle. A star that
     * leaves the window is reborn near the centre.
     */
    fun step(dtMs: Float, speedFraction: Float) {
        val speed = speedFraction.coerceIn(0f, 1f)
        if (speed <= 0f || dtMs <= 0f) return
        val dt = dtMs.coerceAtMost(MAX_STEP_MS)
        val base = speed * RATE_PER_MS * dt
        for (i in angle.indices) {
            // Accelerate as they near the edge, like perspective.
            radius[i] += base * depth[i] * (0.3f + radius[i] * 2f)
            if (radius[i] > 1f) spawn(i, anywhere = false)
        }
    }

    private fun spawn(i: Int, anywhere: Boolean) {
        angle[i] = (random.nextFloat() * 2f * PI).toFloat()
        radius[i] = if (anywhere) random.nextFloat() else random.nextFloat() * NEW_STAR_RADIUS
        depth[i] = 0.3f + random.nextFloat() * 0.7f
        warm[i] = random.nextFloat() < WARM_SHARE
    }

    companion object {
        const val DEFAULT_COUNT = 180
        /** Radius per millisecond at full speed and full depth near the centre. */
        const val RATE_PER_MS = 0.00012f
        const val MAX_STEP_MS = 100f
        const val NEW_STAR_RADIUS = 0.08f
        const val WARM_SHARE = 0.12f

        /** Drift rate from road speed: still when parked, full at [FULL_SPEED_KMH] and above. */
        fun speedFraction(kmh: Double?): Float =
            if (kmh == null || kmh.isNaN() || kmh <= 0.0) 0f else (kmh / FULL_SPEED_KMH).toFloat().coerceIn(0f, 1f)

        const val FULL_SPEED_KMH = 110.0
    }
}
