package com.aiconsole.kittlauncher2.ui.components

import kotlin.math.PI
import kotlin.math.floor

/**
 * Timing for the hologram car's turntable. Plain Kotlin so it is unit-tested: which two rendered
 * frames to show for a yaw and how far to blend between them, and the order to preload frames in.
 */
object Turntable {

    private const val TWO_PI = (2 * PI).toFloat()

    /** Radians per second the car turns: one revolution in about 26 s. */
    const val SPEED = 0.24f

    /** Two neighbouring frames and the blend from [from] (0) to [to] (1). */
    data class Blend(val from: Int, val to: Int, val mix: Float)

    fun wrap(yaw: Float): Float = ((yaw % TWO_PI) + TWO_PI) % TWO_PI

    /** Yaw after [elapsedMs] of turning; time-based, so a late tick doesn't slow the car down. */
    fun advance(yaw: Float, elapsedMs: Long): Float = wrap(yaw + SPEED * elapsedMs.coerceIn(0L, 250L) / 1000f)

    fun blend(yaw: Float, frames: Int): Blend {
        if (frames <= 1) return Blend(0, 0, 0f)
        val position = wrap(yaw) / TWO_PI * frames
        val from = floor(position).toInt().coerceIn(0, frames - 1)
        return Blend(from, (from + 1) % frames, (position - from).coerceIn(0f, 1f))
    }

    /** Frame order to decode in: the [start] frame first, then onward in turning order. */
    fun preloadOrder(start: Int, frames: Int): List<Int> =
        if (frames <= 0) emptyList() else List(frames) { ((start % frames + frames) % frames + it) % frames }
}
