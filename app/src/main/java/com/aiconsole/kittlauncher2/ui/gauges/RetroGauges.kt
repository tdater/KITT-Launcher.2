package com.aiconsole.kittlauncher2.ui.gauges

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.aiconsole.kittlauncher2.obd.GaugeConfig
import com.aiconsole.kittlauncher2.obd.PidDefinition
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashBlue
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.KittCharcoal
import com.aiconsole.kittlauncher2.ui.theme.KittGraphite
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.LocalLiteMode
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** What the retro/futuristic gauge faces need, already resolved for display. */
internal class GaugeFace(
    val config: GaugeConfig,
    val def: PidDefinition?,
    val metric: Boolean,
    /** Current value in display units, or null with no reading. */
    val value: Double?,
    val text: String,
    val fraction: Float,
    val accent: Color,
    val warning: Boolean,
    /** Highest value this drive as a 0..1 fraction (Needle and Halo mark it), or null. */
    val peak: Float? = null
) {
    val low: Double get() = def?.displayValue(def.min, metric) ?: 0.0
    val high: Double get() = def?.displayValue(def.max, metric) ?: 1.0
    val unit: String get() = def?.unit(metric).orEmpty()

    /** Start of the red zone from the warn-above limit, as a 0..1 fraction. */
    val redFrom: Float? get() = config.warnAbove?.let { w -> def?.let { GaugeScale.fraction(w, it.min, it.max) } }
}

internal fun DrawScope.text(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    sizePx: Float,
    color: Color,
    glow: Float = 0f,
    bold: Boolean = true
) {
    if (sizePx < 4f || text.isEmpty()) return
    val layout = measurer.measure(
        text,
        TextStyle(
            fontSize = sizePx.toSp(),
            fontFamily = FontFamily.Monospace,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    )
    drawText(
        layout,
        color = color,
        topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
        shadow = if (glow > 0f) Shadow(color, Offset.Zero, glow) else null
    )
}

internal fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(rad).toFloat(), center.y + radius * sin(rad).toFloat())
}

/** Retro analog instrument: numbered ticks, red zone from the warn limit and a sweeping needle. */
@Composable
internal fun ColumnScope.NeedleBody(face: GaugeFace) {
    val measurer = rememberTextMeasurer()
    val lite = LocalLiteMode.current
    val sweep = animateFloatAsState(face.fraction, if (lite) snap() else tween(280), label = "needle")
    val low = face.low
    val high = face.high
    val divisor = GaugeScale.labelDivisor(high)
    val step = GaugeScale.niceStep(high - low, 6)
    val majors = GaugeScale.ticks(low, high, step)
    val minors = GaugeScale.ticks(low, high, step / 2)
    val redFrom = face.redFrom
    val dial = DashWhite
    val needle = if (face.warning) face.accent else ScannerRed
    val faceColor = KittCharcoal
    val accent = face.accent
    val span = (high - low).takeIf { it > 0 } ?: 1.0

    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val radius = minOf(size.width / 2f, size.height / 1.72f) * 0.96f
        val center = Offset(size.width / 2f, (size.height - radius * 1.72f) / 2f + radius)
        drawCircle(faceColor, radius, center)
        drawCircle(dial.copy(alpha = 0.25f), radius, center, style = Stroke(radius * 0.025f))

        val arcRadius = radius * 0.86f
        val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
        val arcSize = Size(arcRadius * 2, arcRadius * 2)
        drawArc(dial.copy(alpha = 0.35f), 135f, 270f, false, arcTopLeft, arcSize, style = Stroke(radius * 0.02f))
        if (redFrom != null && redFrom < 1f) {
            drawArc(
                AlertRed.copy(alpha = 0.8f), GaugeScale.needleAngle(redFrom), 270f * (1f - redFrom), false,
                arcTopLeft, arcSize, style = Stroke(radius * 0.07f)
            )
        }
        minors.forEach { v ->
            val a = GaugeScale.needleAngle(((v - low) / span).toFloat())
            drawLine(dial.copy(alpha = 0.6f), polar(center, arcRadius * 0.92f, a), polar(center, arcRadius, a), radius * 0.02f)
        }
        val labelled = majors.size <= 9
        majors.forEach { v ->
            val a = GaugeScale.needleAngle(((v - low) / span).toFloat())
            drawLine(dial, polar(center, arcRadius * 0.82f, a), polar(center, arcRadius, a), radius * 0.035f)
            if (labelled) text(measurer, GaugeScale.label(v, divisor), polar(center, arcRadius * 0.64f, a), radius * 0.15f, dial)
        }
        if (divisor > 1.0) text(measurer, "x1000", Offset(center.x, center.y - radius * 0.3f), radius * 0.1f, DashGrey, bold = false)

        // Peak-hold marker: a small amber pointer outside the scale at the highest value this drive.
        face.peak?.takeIf { it > 0f }?.let { pk ->
            val a = GaugeScale.needleAngle(pk)
            val mark = Path().apply {
                moveTo(polar(center, arcRadius * 1.0f, a).x, polar(center, arcRadius * 1.0f, a).y)
                lineTo(polar(center, arcRadius * 1.12f, a - 4f).x, polar(center, arcRadius * 1.12f, a - 4f).y)
                lineTo(polar(center, arcRadius * 1.12f, a + 4f).x, polar(center, arcRadius * 1.12f, a + 4f).y)
                close()
            }
            drawPath(mark, DashAmber)
        }
        val angle = GaugeScale.needleAngle(sweep.value)
        val tip = polar(center, arcRadius * 0.96f, angle)
        val tail = polar(center, radius * 0.16f, angle + 180f)
        drawLine(needle.copy(alpha = 0.25f), tail, tip, radius * 0.09f, cap = StrokeCap.Round)
        drawLine(needle, tail, tip, radius * 0.035f, cap = StrokeCap.Round)
        drawCircle(KittGraphite, radius * 0.1f, center)
        drawCircle(needle, radius * 0.1f, center, style = Stroke(radius * 0.02f))

        text(measurer, face.text, Offset(center.x, center.y + radius * 0.42f), radius * 0.24f, accent, glow = radius * 0.08f)
    }
}

/** Cold-cathode Nixie tubes: glass, anode mesh, the faint digit stack and the lit numeral. */
@Composable
internal fun ColumnScope.NixieBody(face: GaugeFace) {
    val measurer = rememberTextMeasurer()
    val glass = DashGrey
    val tubeFill = KittCharcoal
    val accent = face.accent
    val cells = GaugeScale.cells(face.text, 4)

    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val count = cells.size
        val slot = size.width / count
        val tubeW = minOf(slot * 0.9f, size.height * 0.62f)
        val tubeH = minOf(size.height, tubeW * 1.65f)
        val top = (size.height - tubeH) / 2f
        val gap = slot - tubeW
        cells.forEachIndexed { i, (ch, dot) ->
            val left = i * slot + gap / 2f
            val corner = CornerRadius(tubeW * 0.45f, tubeW * 0.45f)
            drawRoundRect(tubeFill, Offset(left, top), Size(tubeW, tubeH), corner)
            // Anode mesh.
            var y = top + tubeH * 0.12f
            while (y < top + tubeH * 0.9f) {
                drawLine(glass.copy(alpha = 0.1f), Offset(left + tubeW * 0.12f, y), Offset(left + tubeW * 0.88f, y), 1f)
                y += tubeH * 0.06f
            }
            val middle = Offset(left + tubeW / 2f, top + tubeH * 0.52f)
            // Unlit cathodes stacked behind the numeral.
            text(measurer, "8", middle, tubeH * 0.62f, accent.copy(alpha = 0.07f))
            if (ch != ' ') text(measurer, ch.toString(), middle, tubeH * 0.62f, accent, glow = tubeH * 0.18f, bold = false)
            if (dot) drawCircle(accent, tubeW * 0.06f, Offset(left + tubeW * 0.86f, top + tubeH * 0.82f))
            drawRoundRect(glass.copy(alpha = 0.45f), Offset(left, top), Size(tubeW, tubeH), corner, style = Stroke(1.5f))
            // Glass highlight.
            drawLine(
                Color.White.copy(alpha = 0.12f),
                Offset(left + tubeW * 0.2f, top + tubeH * 0.15f),
                Offset(left + tubeW * 0.2f, top + tubeH * 0.45f),
                tubeW * 0.05f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Eighties digital-dash stair steps: VFD bars that rise taller left to right, cyan to hot red. */
@Composable
internal fun ColumnScope.SkylineBody(face: GaugeFace) {
    val cool = DashBlue
    val warm = DashAmber
    val hot = ScannerRed
    val back = KittBlack
    val danger = AlertRed
    val bars = 16
    val heights = GaugeScale.skylineHeights(bars)
    val lit = GaugeScale.lit(face.fraction, bars)
    SegmentDisplay(face.text, Modifier.fillMaxWidth().weight(0.42f), face.accent, cells = 4)
    Canvas(Modifier.fillMaxWidth().weight(0.58f)) {
        val gap = size.width * 0.012f
        val barW = (size.width - gap * (bars - 1)) / bars
        heights.forEachIndexed { i, h ->
            val t = i / (bars - 1f)
            val color = if (face.warning) danger else zone(t, cool, warm, hot)
            val barH = size.height * h
            drawRect(
                if (i < lit) color else color.copy(alpha = 0.1f),
                Offset(i * (barW + gap), size.height - barH),
                Size(barW, barH)
            )
        }
        // VFD grid: thin dark rows cut across every bar.
        val pitch = maxOf(size.height / 14f, 3f)
        var y = size.height - pitch
        while (y > 0f) {
            drawLine(back, Offset(0f, y), Offset(size.width, y), pitch * 0.28f)
            y -= pitch
        }
    }
}

/** Fighter-jet HUD tape: the scale scrolls under a fixed caret, with the value boxed above. */
@Composable
internal fun ColumnScope.TapeBody(face: GaugeFace) {
    val measurer = rememberTextMeasurer()
    val hud = if (face.warning) face.accent else LedGreen
    val band = KittCharcoal
    val low = face.low
    val high = face.high
    val window = ((high - low) * 0.3).takeIf { it > 0 } ?: 1.0
    val step = GaugeScale.niceStep(window, 4)
    val divisor = GaugeScale.labelDivisor(high)
    val value = (face.value ?: low).coerceIn(minOf(low, high), maxOf(low, high))
    val marks = GaugeScale.ticks(value - window / 2, value + window / 2, step / 2)
        .filter { it >= minOf(low, high) - 1e-9 && it <= maxOf(low, high) + 1e-9 }

    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val tapeTop = size.height * 0.48f
        val tapeH = size.height * 0.5f
        drawRect(band, Offset(0f, tapeTop), Size(size.width, tapeH))
        drawLine(hud.copy(alpha = 0.5f), Offset(0f, tapeTop), Offset(size.width, tapeTop), 1.5f)
        val mid = size.width / 2f
        marks.forEach { v ->
            val x = mid + ((v - value) / window * size.width).toFloat()
            val fade = (1f - (abs(x - mid) / mid).let { it * it }).coerceIn(0f, 1f)
            val major = GaugeScale.isMultiple(v, step)
            val len = tapeH * if (major) 0.42f else 0.22f
            drawLine(hud.copy(alpha = fade), Offset(x, tapeTop), Offset(x, tapeTop + len), if (major) 3f else 2f)
            if (major) {
                text(measurer, GaugeScale.label(v, divisor), Offset(x, tapeTop + tapeH * 0.72f), tapeH * 0.32f, hud.copy(alpha = fade), bold = false)
            }
        }
        // Fixed caret.
        val caret = Path().apply {
            moveTo(mid, tapeTop + tapeH * 0.3f)
            lineTo(mid - tapeH * 0.16f, tapeTop)
            lineTo(mid + tapeH * 0.16f, tapeTop)
            close()
        }
        drawPath(caret, hud)
        // Boxed readout with HUD brackets.
        val boxH = size.height * 0.4f
        val boxW = minOf(size.width * 0.6f, boxH * 3.2f)
        val boxTop = tapeTop - boxH - size.height * 0.04f
        val boxLeft = mid - boxW / 2f
        drawRect(KittBlack, Offset(boxLeft, boxTop), Size(boxW, boxH))
        drawRect(hud, Offset(boxLeft, boxTop), Size(boxW, boxH), style = Stroke(2f))
        val tick = boxH * 0.3f
        listOf(boxLeft - 6f to -1f, boxLeft + boxW + 6f to 1f).forEach { (x, dir) ->
            drawLine(hud, Offset(x, boxTop), Offset(x, boxTop + boxH), 2f)
            drawLine(hud, Offset(x, boxTop), Offset(x - dir * tick, boxTop), 2f)
            drawLine(hud, Offset(x, boxTop + boxH), Offset(x - dir * tick, boxTop + boxH), 2f)
        }
        text(measurer, face.text, Offset(mid, boxTop + boxH / 2f), boxH * 0.68f, hud, glow = boxH * 0.15f)
    }
}

/** CRT oscilloscope: phosphor trace of recent samples over a graticule, with scanlines. */
@Composable
internal fun ColumnScope.ScopeBody(face: GaugeFace, history: GaugeHistory) {
    val measurer = rememberTextMeasurer()
    val phosphor = if (face.warning) face.accent else LedGreen
    val screen = KittBlack
    val danger = AlertRed
    val def = face.def
    val redFrom = face.redFrom
    val values = history.values()

    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val corner = CornerRadius(size.minDimension * 0.08f)
        drawRoundRect(screen, Offset.Zero, size, corner)
        // Graticule: 8 x 4 divisions with a brighter centre cross.
        for (k in 1 until 8) {
            val x = size.width * k / 8f
            drawLine(phosphor.copy(alpha = if (k == 4) 0.22f else 0.1f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        for (k in 1 until 4) {
            val y = size.height * k / 4f
            drawLine(phosphor.copy(alpha = if (k == 2) 0.22f else 0.1f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (redFrom != null) {
            val y = size.height * (1f - redFrom)
            drawLine(
                danger.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }
        if (def != null && values.size >= 2) {
            val path = Path()
            var last = Offset.Zero
            values.forEachIndexed { index, v ->
                val x = size.width * index / (GaugeHistory.CAPACITY - 1f)
                val y = size.height * (1f - GaugeScale.fraction(v, def.min, def.max))
                last = Offset(x, y)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val w = size.minDimension * 0.02f
            drawPath(path, phosphor.copy(alpha = 0.12f), style = Stroke(w * 4f, cap = StrokeCap.Round))
            drawPath(path, phosphor.copy(alpha = 0.3f), style = Stroke(w * 2f, cap = StrokeCap.Round))
            drawPath(path, phosphor, style = Stroke(w, cap = StrokeCap.Round))
            drawCircle(phosphor.copy(alpha = 0.35f), w * 3.5f, last)
            drawCircle(Color.White.copy(alpha = 0.9f), w * 1.2f, last)
        }
        // Scanlines.
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 1f)
            y += 3f
        }
        val tag = size.height * 0.2f
        text(measurer, face.text, Offset(size.width - tag * 2.2f, tag * 0.8f), tag, phosphor, glow = tag * 0.4f)
        drawRoundRect(phosphor.copy(alpha = 0.35f), Offset.Zero, size, corner, style = Stroke(1.5f))
    }
}

/** Tron-style neon halo: a segmented ring that fills cyan to hot, the value in the centre. */
@Composable
internal fun ColumnScope.HaloBody(face: GaugeFace) {
    val measurer = rememberTextMeasurer()
    val cool = DashBlue
    val warm = DashAmber
    val hot = ScannerRed
    val danger = AlertRed
    val unitColor = DashGrey
    val accent = face.accent
    val segments = 36
    val lit = GaugeScale.lit(face.fraction, segments)
    val unit = face.unit.uppercase()

    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        val radius = size.minDimension / 2f * 0.94f
        val center = Offset(size.width / 2f, size.height / 2f)
        val ring = radius * 0.16f
        val r = radius - ring / 2f
        val topLeft = Offset(center.x - r, center.y - r)
        val box = Size(r * 2, r * 2)
        val start = 120f
        val sweep = 300f
        val each = sweep / segments
        for (i in 0 until segments) {
            val t = i / (segments - 1f)
            val color = if (face.warning) danger else zone(t, cool, warm, hot)
            drawArc(
                if (i < lit) color else color.copy(alpha = 0.12f), start + i * each, each * 0.7f, false, topLeft, box,
                style = Stroke(ring)
            )
        }
        drawCircle(cool.copy(alpha = 0.3f), radius, center, style = Stroke(1.5f))
        drawCircle(cool.copy(alpha = 0.2f), radius - ring - 3f, center, style = Stroke(1f))
        // Peak-hold marker: a bright bar across the ring at the highest value this drive.
        face.peak?.takeIf { it > 0f }?.let { pk ->
            val a = start + sweep * pk
            drawLine(Color.White.copy(alpha = 0.9f), polar(center, r - ring * 0.75f, a), polar(center, r + ring * 0.75f, a), 2.5f)
        }
        if (lit > 0) {
            val t = (lit - 1) / (segments - 1f)
            val color = if (face.warning) danger else zone(t, cool, warm, hot)
            val tip = polar(center, r, start + lit * each - each * 0.65f)
            drawCircle(color.copy(alpha = 0.18f), ring * 1.4f, tip)
            drawCircle(color.copy(alpha = 0.35f), ring * 0.9f, tip)
            drawCircle(Color.White.copy(alpha = 0.85f), ring * 0.3f, tip)
        }
        text(measurer, face.text, Offset(center.x, center.y - radius * 0.06f), radius * 0.38f, accent, glow = radius * 0.1f)
        text(measurer, unit, Offset(center.x, center.y + radius * 0.34f), radius * 0.14f, unitColor, bold = false)
    }
}

/** Dash colour zones along a scale: cool for most of it, amber near the top, hot at the end. */
private fun zone(t: Float, cool: Color, warm: Color, hot: Color): Color = when {
    t < 0.6f -> cool
    t < 0.85f -> warm
    else -> hot
}
