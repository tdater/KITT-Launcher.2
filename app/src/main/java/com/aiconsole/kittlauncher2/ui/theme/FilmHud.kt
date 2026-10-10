package com.aiconsole.kittlauncher2.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/** Text the film HUD themes show in the status strip, panels and screensaver. */
object FilmHudText {

    /** Readouts in the style of the T-800's vision overlays. */
    val TERMINATOR = listOf(
        "SCAN MODE 43984",
        "ANALYSIS: COMPLETE",
        "THREAT ASSESSMENT: NONE",
        "SYSTEMS: NOMINAL",
        "MATCH: VEHICLE PROFILE",
        "PRIORITY: MISSION"
    )

    /** RoboCop's published prime directives; the fourth stays classified, as in the film. */
    val ROBOCOP = listOf(
        "DIRECTIVE 1: SERVE THE PUBLIC TRUST",
        "DIRECTIVE 2: PROTECT THE INNOCENT",
        "DIRECTIVE 3: UPHOLD THE LAW",
        "DIRECTIVE 4: [CLASSIFIED]"
    )

    /** Short terminal lines in the style of the film's opening messages. */
    val MATRIX = listOf(
        "WAKE UP, NEO...",
        "THE MATRIX HAS YOU...",
        "FOLLOW THE WHITE RABBIT.",
        "KNOCK, KNOCK."
    )

    fun lines(style: HudStyle): List<String> = when (style) {
        HudStyle.Terminator -> TERMINATOR
        HudStyle.RoboCop -> ROBOCOP
        HudStyle.Matrix -> MATRIX
    }

    /** Brand line for the status strip. */
    fun brand(style: HudStyle): String = when (style) {
        HudStyle.Terminator -> "CYBERDYNE 101"
        HudStyle.RoboCop -> "OCP · METRO WEST"
        HudStyle.Matrix -> "NEBUCHADNEZZAR · OPERATOR"
    }

    /** Machine-looking tag for a panel title, e.g. "0x5E2A": stable per title. */
    fun hexTag(title: String): String = "0x%04X".format(title.uppercase().hashCode() and 0xFFFF)
}

/**
 * Full-screen HUD layer drawn over the launcher (never intercepts touches): CRT scanlines, plus a
 * red vision vignette (Terminator), a cold visor glow at the edges (RoboCop) or green CRT bloom (The Matrix).
 */
@Composable
fun FilmHudOverlay(style: HudStyle, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pitch = 3.dp.toPx()
        var y = 0f
        val line = Color.Black.copy(alpha = if (style == HudStyle.Terminator) 0.22f else 0.16f)
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += pitch
        }
        if (style == HudStyle.Matrix) {
            // Green phosphor bloom at the edges of an old monochrome CRT.
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x3300FF41)),
                    center = center,
                    radius = size.maxDimension * 0.8f
                )
            )
        } else if (style == HudStyle.Terminator) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x55FF1A0A)),
                    center = center,
                    radius = size.maxDimension * 0.75f
                )
            )
        } else {
            val glow = Color(0x3366CCFF)
            drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), startY = 0f, endY = size.height * 0.12f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = size.height * 0.88f, endY = size.height))
        }
    }
}

/**
 * Panel frame marks: RoboCop draws targeting brackets on the four corners; Terminator draws
 * short corner ticks like the T-800's framing marks.
 */
fun Modifier.hudFrame(style: HudStyle?, accent: Color): Modifier = if (style == null || style == HudStyle.Matrix) this else drawWithContent {
    drawContent()
    val len = (if (style == HudStyle.RoboCop) 18.dp else 10.dp).toPx()
    val inset = 3.dp.toPx()
    val w = (if (style == HudStyle.RoboCop) 2.dp else 1.5.dp).toPx()
    val c = accent.copy(alpha = 0.9f)
    val l = inset
    val t = inset
    val r = size.width - inset
    val b = size.height - inset
    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(c, Offset(x, y), Offset(x + dx * len, y), w, StrokeCap.Square)
        drawLine(c, Offset(x, y), Offset(x, y + dy * len), w, StrokeCap.Square)
    }
    corner(l, t, 1f, 1f)
    corner(r, t, -1f, 1f)
    corner(l, b, 1f, -1f)
    corner(r, b, -1f, -1f)
}
