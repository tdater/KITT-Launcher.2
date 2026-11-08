package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure maths for the hologram's stage effects (projector ring, light beam, rising data motes,
 * flicker). Plain Kotlin so it is unit-tested; the drawing below only places what this returns.
 */
object HoloFx {

    /** Number of data motes rising through the beam. */
    const val MOTES = 18

    /** A rising mote: [x] across the beam (-1..1), [rise] up the beam (0 at the base..1 at the top), [alpha] fades at both ends. */
    data class Mote(val x: Float, val rise: Float, val alpha: Float)

    /** Mote [index] at clock [phase] (any float, wraps every 1): the same inputs always give the same place. */
    fun mote(index: Int, phase: Float): Mote {
        val seed = (index * 0.61803398f) % 1f
        val rise = ((phase * (0.6f + 0.5f * seed) + seed * 7.13f) % 1f + 1f) % 1f
        val x = ((seed * 11.7f) % 2f) - 1f
        val alpha = (sin(rise * PI.toFloat())).coerceIn(0f, 1f) * 0.55f
        return Mote(x * 0.85f, rise, alpha)
    }

    /** Brightness multiplier for the projection: steady, with a brief dip about once every four turns of [phase]. */
    fun flicker(phase: Float): Float {
        val t = ((phase * 4f) % 1f + 1f) % 1f
        return if (t > 0.965f) 0.82f else 1f
    }

    /** The tach arc turns amber near the redline and red past it. */
    fun rpmWarn(rpm: Float): Int = when {
        rpm >= 0.92f -> 2
        rpm >= 0.80f -> 1
        else -> 0
    }

    /** How the effects look for one theme; the colours of the hologram itself come from the theme as before. */
    data class Style(val fringe: Color, val ticks: Int, val scanlineAlpha: Float, val lamp: Color, val bloom: Float)

    /** Seconds for one slow in-and-out of the hologram's glow. */
    const val BREATH_SECONDS = 4.5f

    /** Glow level 0..1 at [phase] (any float, wraps every 1): eases in and out like a breath, with no sudden turn at the ends. */
    fun breath(phase: Float): Float {
        val t = ((phase % 1f) + 1f) % 1f
        return 0.5f - 0.5f * cos(t * 2f * PI.toFloat())
    }

    /** Each film theme gets its own fringe colour, tick count (BTTF turns 88) and scanline strength (the Matrix is the heaviest). */
    fun style(skin: CockpitSkin): Style = when (skin) {
        CockpitSkin.Kitt -> Style(Color(0xFF40E0FF), 72, 0.05f, Color(0xFFFF2A2A), 1.0f)
        CockpitSkin.Terminator -> Style(Color(0xFF40E0FF), 60, 0.06f, Color(0xFFFF3B30), 1.0f)
        CockpitSkin.Matrix -> Style(Color(0xFFB8FFC8), 72, 0.09f, Color(0xFF7CFF9A), 1.25f)
        CockpitSkin.Future -> Style(Color(0xFF4DB8FF), 88, 0.05f, Color(0xFFFF9A2A), 1.1f)
        CockpitSkin.RoboCop -> Style(Color(0xFF6AA8FF), 64, 0.07f, Color(0xFF8FC0FF), 0.9f)
    }

    /** The point on a floor-plane ellipse (radii [rx], [ry]) at [angle] radians. */
    fun ellipsePoint(cx: Float, cy: Float, rx: Float, ry: Float, angle: Float): Offset =
        Offset(cx + rx * cos(angle), cy + ry * sin(angle))
}

/**
 * The projector the hologram stands on: a floor ring with turning tick marks, a tach arc (engine
 * speed), a speed arc, a radar sweep, and a faint light beam with data motes rising through it.
 * Everything is driven by the live readings, so it stays functional, not just decoration.
 */
internal fun DrawScope.drawProjector(
    line: Color, hot: Color, warn: Color, alert: Color?,
    cx: Float, floorY: Float, rx: Float, height: Float,
    rpm: Float, speedFrac: Float, yaw: Float, pulse: Float, ticks: Int = 72
) {
    val thin = 1.dp.toPx()
    val ry = rx * 0.17f
    val ring = alert ?: line

    // Light beam: a cone of light from the ring up past the roof, brighter at the base.
    val beam = Path().apply {
        moveTo(cx - rx * 0.92f, floorY)
        lineTo(cx - rx * 0.74f, floorY - height)
        lineTo(cx + rx * 0.74f, floorY - height)
        lineTo(cx + rx * 0.92f, floorY)
        close()
    }
    drawPath(beam, Brush.verticalGradient(listOf(Color.Transparent, ring.copy(alpha = 0.10f)), startY = floorY - height, endY = floorY))

    // Floor ring, an inner ring, and the ticks that turn with the car.
    drawOval(ring.copy(alpha = 0.55f), Offset(cx - rx, floorY - ry), Size(rx * 2f, ry * 2f), style = Stroke(thin * 1.4f))
    drawOval(ring.copy(alpha = 0.25f), Offset(cx - rx * 0.78f, floorY - ry * 0.78f), Size(rx * 1.56f, ry * 1.56f), style = Stroke(thin))
    for (k in 0 until ticks) {
        val a = yaw + k * (2f * PI.toFloat() / ticks)
        val long = k % 6 == 0
        val outer = HoloFx.ellipsePoint(cx, floorY, rx * 1.04f, ry * 1.04f, a)
        val inner = HoloFx.ellipsePoint(cx, floorY, rx * (if (long) 0.95f else 0.99f), ry * (if (long) 0.95f else 0.99f), a)
        drawLine(ring.copy(alpha = if (long) 0.7f else 0.35f), inner, outer, thin)
    }

    // Radar sweep: a soft wedge on the floor turning once per pulse cycle.
    val sweepStart = (pulse * 360f) % 360f
    drawArc(
        brush = Brush.radialGradient(listOf(ring.copy(alpha = 0.22f), Color.Transparent), center = Offset(cx, floorY), radius = rx),
        startAngle = sweepStart, sweepAngle = 38f, useCenter = true,
        topLeft = Offset(cx - rx * 0.78f, floorY - ry * 0.78f), size = Size(rx * 1.56f, ry * 1.56f)
    )

    // Tach arc along the near edge (engine speed), and speed arc inside it.
    val tach = rpm.coerceIn(0f, 1f)
    if (tach > 0.01f) {
        val tachColor = when (HoloFx.rpmWarn(tach)) {
            2 -> alert ?: Color(0xFFFF3B30)
            1 -> warn
            else -> hot
        }
        drawArc(tachColor.copy(alpha = 0.9f), startAngle = 180f, sweepAngle = -180f * tach, useCenter = false,
            topLeft = Offset(cx - rx * 1.12f, floorY - ry * 1.12f), size = Size(rx * 2.24f, ry * 2.24f), style = Stroke(thin * 3f))
    }
    val spd = speedFrac.coerceIn(0f, 1f)
    if (spd > 0.01f) {
        drawArc(hot.copy(alpha = 0.7f), startAngle = 0f, sweepAngle = 180f * spd, useCenter = false,
            topLeft = Offset(cx - rx * 1.22f, floorY - ry * 1.22f), size = Size(rx * 2.44f, ry * 2.44f), style = Stroke(thin * 2f))
    }

    // Data motes drifting up the beam.
    for (i in 0 until HoloFx.MOTES) {
        val m = HoloFx.mote(i, pulse)
        if (m.alpha <= 0.02f) continue
        val width = rx * (0.92f + (0.74f - 0.92f) * m.rise)
        drawCircle(hot.copy(alpha = m.alpha), thin * 1.3f, Offset(cx + m.x * width, floorY - m.rise * height))
    }
}

/** Faint horizontal scanlines over the projection, like a volumetric display. */
internal fun DrawScope.drawScanlines(color: Color, alpha: Float, left: Float, top: Float, width: Float, height: Float) {
    val step = 3.dp.toPx()
    var y = top
    while (y < top + height) {
        drawLine(color.copy(alpha = alpha), Offset(left, y), Offset(left + width, y), 1f)
        y += step
    }
}
