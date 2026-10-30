package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import kotlin.math.abs
import kotlin.math.hypot

/** A plain rectangle, so the layout rules stay pure Kotlin and unit-testable. */
data class CalloutRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    fun overlaps(o: CalloutRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/**
 * Where the live-data callouts sit around the hologram: a row of four above it (engine, speed, battery, fuel) and a row of four
 * below it (the four tyres / fallback engine PIDs), with the vehicle in the band between, so no box covers the car. Slots 0..3
 * are the top row left to right and 4..7 the bottom row.
 */
object CalloutLayout {
    const val SLOTS = 8

    class Boxes(val rects: List<CalloutRect>, val bandTop: Float, val bandBottom: Float) {
        val bandHeight: Float get() = (bandBottom - bandTop).coerceAtLeast(1f)

        /** The slot under a touch, or -1. */
        fun hit(x: Float, y: Float, slop: Float = 0f): Int =
            rects.indexOfFirst { CalloutRect(it.left - slop, it.top - slop, it.right + slop, it.bottom + slop).contains(x, y) }
    }

    /** [dp] is the size of one dp in pixels, so the same rules serve every density. */
    fun boxes(width: Float, height: Float, dp: Float): Boxes {
        val pad = 2f * dp
        val gap = 4f * dp
        // Sleeker callout height leaves a larger central band for the rotating 3D vehicle.
        val boxH = (height * 0.165f).coerceIn(22f * dp, 34f * dp)
        val boxW = ((width - 2f * pad - 3f * gap) / 4f).coerceAtLeast(1f)
        val rects = ArrayList<CalloutRect>(SLOTS)
        for (row in 0..1) {
            val top = if (row == 0) pad else height - pad - boxH
            for (col in 0 until 4) {
                val left = pad + col * (boxW + gap)
                rects += CalloutRect(left, top, left + boxW, top + boxH)
            }
        }
        return Boxes(rects, pad + boxH + gap, height - pad - boxH - gap)
    }

    /** Orders each row (slots 0..3 above, 4..7 below) left to right by its part's X coordinate. */
    fun assignSlots(callouts: List<Callout>, anchorX: (String) -> Float) {
        for (top in listOf(true, false)) {
            val row = callouts.filter { (it.slot < 4) == top }.sortedBy { anchorX(it.anchor) }
            row.forEachIndexed { i, c -> c.slot = (if (top) 0 else 4) + i }
        }
    }

    /**
     * Hit-tests the anatomical anchor positions on the rotating 3D vehicle itself.
     * Returns the callout [key] whose vehicle anchor `(ax, ay)` is closest to `(x, y)` within [maxRadius],
     * or null if the touch was not on a polled vehicle component.
     */
    fun hitAnchor(
        x: Float,
        y: Float,
        callouts: List<Callout>,
        anchorPos: (String) -> Offset,
        maxRadius: Float
    ): String? {
        var bestKey: String? = null
        var bestDist = maxRadius
        for (c in callouts) {
            val pt = anchorPos(c.anchor)
            val d = hypot(x - pt.x, y - pt.y)
            if (d <= bestDist) {
                bestDist = d
                bestKey = c.key
            }
        }
        return bestKey
    }
}

/** One live value shown as a sleek HUD badge with a circuit leader line to the part of the vehicle it describes. */
class Callout(
    val key: String,
    val title: String,
    val value: String,
    val sub: String?,
    val level: ReadoutLevel,
    /** Starts as the row it belongs to (0..3 top, 4..7 bottom); the draw pass orders each row by where its part is on screen. */
    var slot: Int,
    /** "engine", "battery", "fuel", "roof" or "w0".."w3" (a wheel hub). */
    val anchor: String
)

/** Everything the info card shows once a vehicle part or callout is tapped. */
class CalloutDetail(
    val title: String,
    val value: String,
    val level: ReadoutLevel,
    val rows: List<Pair<String, String>>,
    val about: String
)

private fun chamferBoxPath(r: CalloutRect, cut: Float): Path = Path().apply {
    moveTo(r.left + cut, r.top)
    lineTo(r.right - cut * 0.4f, r.top)
    lineTo(r.right, r.top + cut * 0.4f)
    lineTo(r.right, r.bottom - cut)
    lineTo(r.right - cut, r.bottom)
    lineTo(r.left + cut * 0.4f, r.bottom)
    lineTo(r.left, r.bottom - cut * 0.4f)
    lineTo(r.left, r.top + cut)
    close()
}

/**
 * Sleek, futuristic holographic callouts: chamfered dark-glass HUD capsules, angled sci-fi circuit leader lines
 * with glowing elbow nodes, and interactive touch target reticles directly on each polled vehicle area.
 */
fun DrawScope.drawCallouts(
    callouts: List<Callout>,
    boxes: CalloutLayout.Boxes,
    anchorFor: (String) -> Offset,
    line: Color,
    selected: String?,
    levelColor: (ReadoutLevel) -> Color
) {
    val dp = 1.dp.toPx()
    // Within each row, boxes follow their parts left to right, so the leader lines stay short as the vehicle turns.
    CalloutLayout.assignSlots(callouts) { anchorFor(it).x }
    val titlePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 7.8f * dp
        letterSpacing = 0.08f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    val valuePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 12f * dp
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    val subPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 7.2f * dp
        typeface = android.graphics.Typeface.MONOSPACE
    }

    for (c in callouts) {
        val r = boxes.rects.getOrNull(c.slot) ?: continue
        val on = c.key == selected
        val needsAttention = c.level == ReadoutLevel.Warn || c.level == ReadoutLevel.Bad
        val statusCol = levelColor(c.level)
        val accentCol = when {
            needsAttention -> statusCol
            on -> Color.White
            else -> line
        }

        // Angled 2-segment circuit leader from the chamfered HUD badge to the interactive touch node on the 3D vehicle.
        val target = anchorFor(c.anchor)
        val portX = target.x.coerceIn(r.left + 10 * dp, r.right - 10 * dp)
        val portY = if (c.slot < 4) r.bottom else r.top
        val port = Offset(portX, portY)
        val dy = target.y - portY
        val kneeY = portY + dy * 0.38f
        val kneeX = portX + (target.x - portX) * 0.35f
        val knee = Offset(kneeX, kneeY)

        val glowAlpha = if (on || needsAttention) 0.42f else 0.18f
        val coreAlpha = if (on || needsAttention) 0.98f else 0.72f
        val glowW = (if (on || needsAttention) 3.4f else 2.2f) * dp
        val coreW = (if (on || needsAttention) 1.5f else 1.0f) * dp

        drawLine(accentCol.copy(alpha = glowAlpha), port, knee, glowW, StrokeCap.Round)
        drawLine(accentCol.copy(alpha = glowAlpha), knee, target, glowW, StrokeCap.Round)
        drawLine(accentCol.copy(alpha = coreAlpha), port, knee, coreW, StrokeCap.Round)
        drawLine(accentCol.copy(alpha = coreAlpha), knee, target, coreW, StrokeCap.Round)
        drawCircle(accentCol.copy(alpha = coreAlpha), 2.0f * dp, port)
        drawCircle(accentCol.copy(alpha = coreAlpha * 0.85f), 1.8f * dp, knee)

        // Interactive touch reticle on the vehicle anchor itself so the user sees each polled part is touchable.
        val nodeR = (if (on) 8.5f else if (needsAttention) 7.5f else 5.2f) * dp
        drawCircle(accentCol.copy(alpha = if (on || needsAttention) 0.32f else 0.16f), nodeR * 1.45f, target)
        drawCircle(accentCol.copy(alpha = if (on || needsAttention) 0.95f else 0.75f), nodeR, target, style = Stroke((if (on) 1.8f else 1.2f) * dp))
        drawCircle(Color.White.copy(alpha = 0.95f), 2.2f * dp, target)

        // Sleek chamfered dark-glass HUD capsule
        val cut = 5.5f * dp
        val boxPath = chamferBoxPath(r, cut)
        drawPath(
            boxPath,
            Brush.verticalGradient(
                listOf(
                    if (on) accentCol.copy(alpha = 0.28f) else Color(0xE0030810),
                    if (needsAttention) statusCol.copy(alpha = 0.22f) else Color(0xB806101C)
                ),
                startY = r.top,
                endY = r.bottom
            )
        )
        drawPath(
            boxPath,
            accentCol.copy(alpha = if (on || needsAttention) 0.95f else 0.55f),
            style = Stroke((if (on || needsAttention) 1.6f else 1.0f) * dp)
        )

        // Glowing laser bar along the leader-facing edge of the capsule
        val barY = if (c.slot < 4) r.bottom - 1.8f * dp else r.top
        drawRect(
            accentCol.copy(alpha = if (on || needsAttention) 1f else 0.75f),
            Offset(r.left + cut, barY),
            Size((r.width - 2 * cut).coerceAtLeast(4f), 1.8f * dp)
        )

        // Status LED diamond / dot in top-right corner
        val pipCenter = Offset(r.right - 6.5f * dp, r.top + 6.5f * dp)
        drawCircle(statusCol.copy(alpha = 0.35f), 4.5f * dp, pipCenter)
        drawCircle(statusCol, 2.3f * dp, pipCenter)

        drawIntoCanvas { cv ->
            val x = r.left + 6.5f * dp
            val compact = r.height < 28f * dp
            titlePaint.color = (if (on) Color.White else accentCol.copy(alpha = 0.90f)).toArgb()
            val maxTitleChars = ((r.width - 18 * dp) / titlePaint.measureText("M")).toInt().coerceAtLeast(3)
            val title = c.title.take(maxTitleChars)
            cv.nativeCanvas.drawText(title, x, r.top + (if (compact) 8.2f else 9.8f) * dp, titlePaint)

            valuePaint.textSize = (if (compact) 10.2f else 12.2f) * dp
            valuePaint.color = (if (needsAttention) statusCol else Color.White).toArgb()
            var text = c.value
            while (text.length > 2 && valuePaint.measureText(text) > r.width - 10 * dp) text = text.dropLast(1)
            cv.nativeCanvas.drawText(text, x, r.top + (if (compact) 18.8f else 21.5f) * dp, valuePaint)

            val subText = c.sub
            if (!compact && subText != null && r.height >= 30f * dp) {
                subPaint.color = Color.White.copy(alpha = 0.70f).toArgb()
                var sub: String = subText
                while (sub.length > 2 && subPaint.measureText(sub) > r.width - 10 * dp) sub = sub.dropLast(1)
                cv.nativeCanvas.drawText(sub, x, r.top + 29.5f * dp, subPaint)
            }
        }
    }
}

/** A perspective grid on the floor under the vehicle, fading into the distance, like the stage in the reference. */
fun DrawScope.drawFloorGrid(line: Color, floorY: Float) {
    val dp = 1.dp.toPx()
    val top = floorY - 2 * dp
    val bottom = size.height
    if (bottom - top < 6 * dp) return
    val vanishY = top - (bottom - top) * 1.4f
    val cx = size.width / 2f
    for (i in 0..6) {
        val t = i / 6f
        val y = top + (bottom - top) * t * t
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, line.copy(alpha = 0.10f + 0.28f * t), Color.Transparent)), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
    for (i in -7..7) {
        val xb = cx + i * size.width * 0.16f
        val xt = cx + (xb - cx) * ((top - vanishY) / (bottom - vanishY))
        drawLine(
            Brush.verticalGradient(listOf(line.copy(alpha = 0.04f), line.copy(alpha = 0.30f)), top, bottom),
            Offset(xt, top), Offset(xb, bottom), 1.dp.toPx()
        )
    }
}

/**
 * Sleek futuristic holographic inspector card that opens when a vehicle part or callout is touched:
 * live value, status badge, session min/max/average telemetry, and diagnostic guidance.
 */
@Composable
fun CalloutCard(detail: CalloutDetail, color: Color, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val shape = chamfer(14f)
    val statusText = when (detail.level) {
        ReadoutLevel.Bad -> "ALERT // ATTENTION REQUIRED"
        ReadoutLevel.Warn -> "ADVISORY // CHECK SENSOR"
        ReadoutLevel.Ok -> "NOMINAL // LIVE OBD TELEMETRY"
        ReadoutLevel.Unknown -> "AWAITING ECU SENSOR DATA"
    }
    Column(
        modifier
            .fillMaxWidth(0.88f)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF2040810), Color(0xEB08121E))
                )
            )
            .border(1.5.dp, color, shape)
            .clickable(onClick = onClose)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Text(
                    detail.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = DashWhite,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                detail.value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = color,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                softWrap = false
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.45f)))
        for ((label, value) in detail.rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = DashGrey, fontFamily = FontFamily.Monospace, maxLines = 1)
                Text(
                    value,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (detail.level == ReadoutLevel.Warn || detail.level == ReadoutLevel.Bad) color else DashWhite,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
        Text(detail.about, style = MaterialTheme.typography.bodySmall, color = DashWhite.copy(alpha = 0.88f))
        Text(
            "TAP CARD OR VEHICLE TO CLOSE · DRAG VEHICLE TO ROTATE 360°",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace
        )
    }
}
