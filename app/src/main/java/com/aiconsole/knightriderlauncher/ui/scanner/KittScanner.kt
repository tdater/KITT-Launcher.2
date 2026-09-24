package com.aiconsole.knightriderlauncher.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/**
 * The always-on KITT front scanner: a black slot with [lamps] lenses in the theme's accent
 * and a persistence trail. The sweep speeds up with engine revs ([rpmFraction], 0..1) and
 * the lamps pulse while KITT is [speaking]. Both are read in the draw pass, so frame and
 * OBD updates only trigger a redraw, never recomposition.
 */
@Composable
fun KittScanner(
    modifier: Modifier = Modifier,
    lamps: Int = 8,
    color: Color = ScannerRed,
    alert: Boolean = false,
    rpmFraction: () -> Float = { 0f },
    speaking: () -> Boolean = { false }
) {
    val engine = remember(lamps) { ScannerEngine(lamps) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(engine) {
        while (true) withFrameNanos { frameNanos = it }
    }

    Canvas(modifier) {
        engine.speed = ScannerEngine.speedFor(alert, rpmFraction())
        val pulse = if (speaking()) ScannerEngine.voicePulse(frameNanos) else 1f
        val levels = engine.step(frameNanos)
        drawRect(Color.Black)
        clipRect {
            val gap = size.height * 0.22f
            val lampWidth = (size.width - gap * (lamps + 1)) / lamps
            val lampHeight = size.height * 0.56f
            val top = (size.height - lampHeight) / 2
            val corner = CornerRadius(lampHeight * 0.18f)
            val hot = lerp(color, Color.White, 0.45f)

            for (i in 0 until lamps) {
                val x = gap + i * (lampWidth + gap)
                val level = levels[i] * pulse
                // Unlit lens: dark ruby glass.
                drawRoundRect(color.copy(alpha = 0.16f), Offset(x, top), Size(lampWidth, lampHeight), corner)
                if (level < 0.02f) continue
                // Bloom spilling around the lens.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.55f * level), Color.Transparent),
                        center = Offset(x + lampWidth / 2, size.height / 2),
                        radius = lampWidth * 0.85f
                    ),
                    topLeft = Offset(x - lampWidth * 0.35f, 0f),
                    size = Size(lampWidth * 1.7f, size.height)
                )
                // Lit lens with a hot filament line through the middle.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.75f * level),
                            hot.copy(alpha = level),
                            color.copy(alpha = 0.75f * level)
                        ),
                        startY = top,
                        endY = top + lampHeight
                    ),
                    topLeft = Offset(x, top),
                    size = Size(lampWidth, lampHeight),
                    cornerRadius = corner
                )
            }
        }
        // Chrome bezel lines top and bottom of the slot.
        drawLine(KittBezel, Offset(0f, 0f), Offset(size.width, 0f), 1f)
        drawLine(KittBezel, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
    }
}
