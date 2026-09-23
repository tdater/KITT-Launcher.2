package com.aiconsole.knightriderlauncher.ui.panes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.LedBarGauge
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/** Live driving readouts overlaid on the navigation pane, KITT-dash style. */
data class HudState(
    val speed: String? = null,
    val speedUnit: String = "MPH",
    val rpmFraction: Float = 0f,
    val rpmText: String? = null,
    val linkLabel: String = "OBD OFFLINE",
    val linked: Boolean = false
)

/**
 * Top pane. Placeholder for the map / navigation module (swap [NavigationVisualizer] for a
 * MapView or Maps composable), with KITT's digital speedometer and bar-graph tach on top.
 */
@Composable
fun NavigationPane(modifier: Modifier = Modifier, hud: HudState = HudState()) {
    KittPanel(
        title = "Navigation",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (hud.linked) LedGreen else DashGrey)
                )
                Text(hud.linkLabel, style = MaterialTheme.typography.labelMedium, color = if (hud.linked) LedGreen else DashGrey)
            }
        }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
        ) {
            NavigationVisualizer(Modifier.fillMaxSize())

            Text(
                text = "MAP MODULE OFFLINE",
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = DashGrey
            )

            // Digital speedometer, bottom right.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.End
            ) {
                SegmentDisplay(
                    text = hud.speed ?: "--",
                    modifier = Modifier.width(150.dp).height(72.dp),
                    color = DashAmber,
                    cells = 3
                )
                Text(hud.speedUnit, style = MaterialTheme.typography.labelMedium, color = DashAmber)
            }

            // Bar-graph tachometer along the bottom edge.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                LedBarGauge(
                    level = hud.rpmFraction,
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    segments = 32
                )
                Text(
                    text = hud.rpmText?.let { "$it RPM" } ?: "RPM",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = DashGrey
                )
            }
        }
    }
}

/** 80s vector-graphics perspective grid with a route line and pulsing position marker. */
@Composable
private fun NavigationVisualizer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nav")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "navPulse"
    )
    Canvas(modifier) {
        val horizon = size.height * 0.3f
        val vanishingPoint = Offset(size.width / 2, horizon)
        val gridColor = ScannerRed.copy(alpha = 0.22f)
        val stroke = 1.dp.toPx()

        val rays = 14
        for (i in -rays..rays) {
            val x = size.width / 2 + i * size.width / rays
            drawLine(gridColor, vanishingPoint, Offset(x, size.height), stroke)
        }
        val rows = 12
        for (row in 1..rows) {
            val t = row.toFloat() / rows
            val y = horizon + (size.height - horizon) * t * t
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), stroke)
        }
        drawLine(ScannerRed.copy(alpha = 0.6f), Offset(0f, horizon), Offset(size.width, horizon), 1.5.dp.toPx())

        val route = Path().apply {
            moveTo(size.width * 0.5f, size.height)
            cubicTo(
                size.width * 0.5f, size.height * 0.75f,
                size.width * 0.64f, size.height * 0.55f,
                size.width * 0.56f, horizon
            )
        }
        drawPath(route, DashAmber.copy(alpha = 0.3f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        drawPath(route, DashAmber, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

        val marker = Offset(size.width * 0.5f, size.height * 0.8f)
        drawCircle(
            color = ScannerRed.copy(alpha = (1f - pulse) * 0.6f),
            radius = 12.dp.toPx() + pulse * 28.dp.toPx(),
            center = marker,
            style = Stroke(2.dp.toPx())
        )
        val arrow = Path().apply {
            moveTo(marker.x, marker.y - 14.dp.toPx())
            lineTo(marker.x - 10.dp.toPx(), marker.y + 10.dp.toPx())
            lineTo(marker.x, marker.y + 4.dp.toPx())
            lineTo(marker.x + 10.dp.toPx(), marker.y + 10.dp.toPx())
            close()
        }
        drawPath(arrow, ScannerRed)
    }
}
