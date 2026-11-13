package com.aiconsole.kittlauncher2.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.browser.WebMedia
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The Bridge cockpit: the launcher seen as a starship bridge. Consoles get chamfered top corners
 * and a light strip along their top edge, buttons get a lit underline, and a starfield viewport
 * drifts behind the translucent panels at road speed. Everything takes its colours from the
 * active palette, so every theme has its own bridge variant (KITT red, KARR gold, Outrun neon,
 * Terminator red, and so on); the Bridge theme itself adds the cyan light strips of the concept.
 *
 * Switched on by the "Bridge cockpit" setting (any theme) or by choosing the Bridge theme.
 * The Matrix keeps its digital rain as its viewport instead of stars.
 */

/** Console shape on the bridge: chamfered top corners, square bottom, like the concept's panels. */
val BridgePanelShape: Shape = CutCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

/** The colour of the bridge's light strips in the active theme. */
val BridgeGlow: Color get() = ThemeState.palette.lightStrip

/**
 * Draws the console light strip along the top edge (inset past the chamfers) with a soft glow
 * under it. Drawn over the content, so put it last in the chain, after the border.
 */
fun Modifier.bridgeLightStrip(glow: Color): Modifier = drawWithContent {
    drawContent()
    val inset = 16.dp.toPx()
    if (size.width <= inset * 2) return@drawWithContent
    val y = 1.dp.toPx()
    drawLine(glow.copy(alpha = 0.22f), Offset(inset, y), Offset(size.width - inset, y), 6.dp.toPx(), StrokeCap.Round)
    drawLine(glow.copy(alpha = 0.95f), Offset(inset, y), Offset(size.width - inset, y), 2.dp.toPx(), StrokeCap.Square)
}

/** A lit underline under a console button: bright while [active], a dim tell-tale otherwise. */
fun Modifier.bridgeUnderline(color: Color, active: Boolean): Modifier = drawWithContent {
    drawContent()
    val side = size.width * 0.26f
    val y = size.height - 3.dp.toPx()
    val c = if (active) color else color.copy(alpha = 0.3f)
    if (active) drawLine(c.copy(alpha = 0.3f), Offset(side, y), Offset(size.width - side, y), 6.dp.toPx(), StrokeCap.Round)
    drawLine(c, Offset(side, y), Offset(size.width - side, y), 2.dp.toPx(), StrokeCap.Round)
}

/**
 * The viewport behind the panels: a dark nebula wash in the theme's colours and a starfield that
 * drifts outward at road speed. Parked, in lite mode, or while a web page is on screen it stands
 * still (one static frame, no frame clock), as the launcher's idle-animation rule requires.
 *
 * [speedKmh] is polled once per frame by the loop, so live readings never recompose anything.
 */
@Composable
fun BridgeStarfield(speedKmh: () -> Double?, modifier: Modifier = Modifier) {
    val lite = LocalLiteMode.current
    val field = remember { Starfield() }
    var tick by remember { mutableLongStateOf(0L) }
    val speed by rememberUpdatedState(speedKmh)
    val paused = lite || WebMedia.pagesOnScreen > 0
    LaunchedEffect(field, paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        var lastStep = 0L
        while (true) {
            if (Starfield.speedFraction(speed()) <= 0f) {
                // Parked: no frame clock at all, just a slow check for the car moving off.
                lastStep = 0L
                delay(PARKED_POLL_MS)
                continue
            }
            withFrameNanos { now ->
                if (now - last >= FRAME_NANOS) {
                    val dtMs = if (lastStep == 0L) 0f else (now - lastStep) / 1_000_000f
                    field.step(dtMs, Starfield.speedFraction(speed()))
                    lastStep = now
                    last = now
                    tick = now
                }
            }
        }
    }
    val star = DashWhite
    val warmStar = DashAmber
    val glow = BridgeGlow
    val warmWash = DashAmber
    // Its own graphics layer, so each frame re-records only the starfield. Without it the tick
    // invalidates the parent layer and every console above the viewport is redrawn on the main
    // thread 30 times a second.
    Canvas(modifier.graphicsLayer {}) {
        if (tick < 0L) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawRect(
            Brush.radialGradient(
                listOf(warmWash.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * 0.28f, h * 0.35f),
                radius = w * 0.4f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * 0.72f, h * 0.62f),
                radius = w * 0.45f
            )
        )
        val cx = w / 2f
        val cy = h * 0.46f
        val reach = hypot(w, h) / 2f
        val px = 1.dp.toPx()
        for (i in 0 until field.size) {
            val r = field.radius[i]
            val x = cx + cos(field.angle[i]) * r * reach
            val y = cy + sin(field.angle[i]) * r * reach
            val s = px * (0.6f + r * 2f * field.depth[i])
            val alpha = (0.2f + r * 1.1f).coerceAtMost(0.9f)
            drawRect(
                color = (if (field.warm[i]) warmStar else star).copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(s, s)
            )
        }
    }
}

// ~30 fps is plenty for slow drift and keeps cheap head units cool.
private const val FRAME_NANOS = 33_000_000L
private const val PARKED_POLL_MS = 500L
