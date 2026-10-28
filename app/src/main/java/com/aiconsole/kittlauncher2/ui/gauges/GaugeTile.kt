package com.aiconsole.kittlauncher2.ui.gauges

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.obd.GaugeConfig
import com.aiconsole.kittlauncher2.obd.GaugeStyle
import com.aiconsole.kittlauncher2.obd.ObdManager
import com.aiconsole.kittlauncher2.obd.PidDefinition
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.components.LedBarGauge
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashYellow
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashBlue
import com.aiconsole.kittlauncher2.ui.theme.ThemeState
import com.aiconsole.kittlauncher2.ui.theme.UiTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything one gauge needs to draw itself. */
data class GaugeUi(val config: GaugeConfig, val def: PidDefinition?, val reading: PidReading?)

/** A live gauge in any [GaugeStyle]. Flashes red when outside the user's limits. */
@Composable
fun GaugeTile(
    gauge: GaugeUi,
    metric: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    header: (@Composable RowScope.() -> Unit)? = null
) {
    val def = gauge.def
    val base = gauge.reading?.value
    val warning = base != null && gauge.config.isWarning(base)
    val shape = MaterialTheme.shapes.small

    val blink = if (warning) {
        val transition = rememberInfiniteTransition(label = "warn")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
            label = "warnAlpha"
        )
        alpha
    } else {
        1f
    }
    val console = ThemeState.theme == UiTheme.Colorado
    // The Matrix shows readouts in code green; KITT uses dash amber.
    val accent = if (warning) AlertRed.copy(alpha = blink) else if (console) LedGreen else DashAmber

    val display = if (def != null && base != null) def.displayValue(base, metric) else null
    val text = if (def != null && display != null) ObdManager.formatValue(display, def) else "--"
    val fraction = if (def != null && base != null) {
        ((base - def.min) / (def.max - def.min)).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, if (warning) AlertRed else KittBezel, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (def?.shortName ?: "Unknown PID").uppercase(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (warning) AlertRed else DashGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(def?.unit(metric).orEmpty(), style = MaterialTheme.typography.labelSmall, color = if (console) DashBlue else DashGrey)
            header?.invoke(this)
        }
        val style = gauge.config.style
        val history = if (style == GaugeStyle.GRAPH || style == GaugeStyle.SCOPE) {
            remember(gauge.config.id) { GaugeHistory() }.also { h -> gauge.reading?.let { h.add(it) } }
        } else {
            null
        }
        val reading = gauge.reading
        val peak = if (def != null && reading != null && (style == GaugeStyle.NEEDLE || style == GaugeStyle.HALO)) {
            GaugeScale.fraction(PeakHold.shared.update(gauge.config.id, reading.value, reading.timestamp), def.min, def.max)
        } else {
            null
        }
        val face = GaugeFace(gauge.config, def, metric, display, text, fraction, accent, warning, peak)
        when (style) {
            GaugeStyle.DIGITAL -> SegmentDisplay(text, Modifier.fillMaxWidth().weight(1f), accent, cells = 4)
            GaugeStyle.BAR -> BarBody(text, fraction, accent)
            GaugeStyle.DIAL -> DialBody(text, fraction, accent)
            GaugeStyle.GRAPH -> GraphBody(text, gauge, accent, history ?: GaugeHistory())
            GaugeStyle.NEEDLE -> NeedleBody(face)
            GaugeStyle.NIXIE -> NixieBody(face)
            GaugeStyle.SKYLINE -> SkylineBody(face)
            GaugeStyle.TAPE -> TapeBody(face)
            GaugeStyle.SCOPE -> ScopeBody(face, history ?: GaugeHistory())
            GaugeStyle.HALO -> HaloBody(face)
            GaugeStyle.FILM -> FilmBody(face)
        }
    }
}

@Composable
private fun ColumnScope.BarBody(text: String, fraction: Float, accent: Color) {
    SegmentDisplay(text, Modifier.fillMaxWidth().weight(1f), accent, cells = 4)
    LedBarGauge(level = fraction, modifier = Modifier.fillMaxWidth().height(10.dp), segments = 16)
}

/** 240° arc of LED ticks, lit up to the value, green → yellow → red. */
@Composable
private fun ColumnScope.DialBody(text: String, fraction: Float, accent: Color) {
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ticks = 30
            val radius = minOf(size.width, size.height * 1.15f) / 2 * 0.92f
            val center = Offset(size.width / 2, size.height / 2 + radius * 0.12f)
            val lit = (fraction * ticks).roundToInt()
            for (i in 0 until ticks) {
                val t = i / (ticks - 1f)
                val angle = Math.toRadians((150 + 240 * t).toDouble())
                val outer = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                val inner = Offset(
                    center.x + radius * 0.78f * cos(angle).toFloat(),
                    center.y + radius * 0.78f * sin(angle).toFloat()
                )
                val color = when {
                    t > 0.85f -> AlertRed
                    t > 0.6f -> DashYellow
                    else -> LedGreen
                }
                drawLine(
                    color = if (i < lit) color else color.copy(alpha = 0.1f),
                    start = inner,
                    end = outer,
                    strokeWidth = radius * 0.07f,
                    cap = StrokeCap.Butt
                )
            }
        }
        SegmentDisplay(text, Modifier.fillMaxWidth(0.55f).fillMaxHeight(0.38f), accent, cells = 4)
    }
}

/** Sparkline of the last ~90 samples with the current value. */
@Composable
private fun ColumnScope.GraphBody(text: String, gauge: GaugeUi, accent: Color, history: GaugeHistory) {
    val def = gauge.def
    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val values = history.values()
            // Faint reference grid.
            for (k in 1..3) {
                val y = size.height * k / 4
                drawLine(KittBezel, Offset(0f, y), Offset(size.width, y), 1f)
            }
            if (def == null || values.size < 2) return@Canvas
            val span = (def.max - def.min).takeIf { it > 0 } ?: 1.0
            val path = Path()
            values.forEachIndexed { index, v ->
                val x = size.width * index / (GaugeHistory.CAPACITY - 1f)
                val y = size.height * (1f - ((v - def.min) / span).toFloat().coerceIn(0f, 1f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, LedGreen, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.padding(start = 6.dp))
        SegmentDisplay(text, Modifier.weight(0.6f).fillMaxHeight(0.6f), accent, cells = 4)
    }
}

/** Ring buffer of recent readings, de-duplicated by timestamp. */
internal class GaugeHistory {
    private val buffer = ArrayDeque<Double>()
    private var lastTimestamp = 0L

    fun add(reading: PidReading) {
        if (reading.timestamp == lastTimestamp) return
        lastTimestamp = reading.timestamp
        buffer.addLast(reading.value)
        while (buffer.size > CAPACITY) buffer.removeFirst()
    }

    fun values(): List<Double> = buffer.toList()

    companion object {
        const val CAPACITY = 90
    }
}

/** Standard gauge-tile size helper for grids. */
val GaugeTileHeight = 120.dp
