package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBlack

/** A plain rectangle, so the layout rules stay pure Kotlin and unit-testable. */
data class CalloutRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    fun overlaps(o: CalloutRect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/**
 * Where the live-data callouts sit around the hologram: a row of four above it (engine, speed, battery, fuel) and a row of four
 * below it (the four tyres), with the vehicle in the band between, so no box covers the car. Slots 0..3 are the top row
 * left to right and 4..7 the bottom row.
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
        val boxH = (height * 0.20f).coerceIn(26f * dp, 40f * dp)
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
}

/** One live value shown as a box with a leader line to the part of the vehicle it describes. */
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

/** Everything the info card shows once a callout is tapped. */
class CalloutDetail(
    val title: String,
    val value: String,
    val level: ReadoutLevel,
    val rows: List<Pair<String, String>>,
    val about: String
)

/**
 * The callouts drawn like the reference: dark glass boxes with corner brackets and white text, a thin leader and a dot on the
 * part. [anchorFor] gives the part's position in this canvas; [selected] is the callout whose card is open.
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
    for (top in listOf(true, false)) {
        val row = callouts.filter { (it.slot < 4) == top }.sortedBy { anchorFor(it.anchor).x }
        row.forEachIndexed { i, c -> c.slot = (if (top) 0 else 4) + i }
    }
    val titlePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 8.5f * dp
        letterSpacing = 0.06f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        color = Color.White.copy(alpha = 0.78f).toArgb()
    }
    val valuePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 13f * dp
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        color = Color.White.toArgb()
    }
    val subPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 8f * dp
        typeface = android.graphics.Typeface.MONOSPACE
        color = Color.White.copy(alpha = 0.62f).toArgb()
    }
    for (c in callouts) {
        val r = boxes.rects.getOrNull(c.slot) ?: continue
        val on = c.key == selected
        // Leader: from the edge of the box that faces the part to a dot on it.
        val target = anchorFor(c.anchor)
        val edge = Offset(
            target.x.coerceIn(r.left + 8 * dp, r.right - 8 * dp),
            if (c.slot < 4) r.bottom else r.top
        )
        val lead = line.copy(alpha = if (on) 0.95f else 0.65f)
        drawLine(lead, edge, target, 1.dp.toPx())
        drawCircle(line.copy(alpha = 0.25f), 5.dp.toPx(), target)
        drawCircle(Color.White.copy(alpha = 0.95f), 2.dp.toPx(), target)

        // Box: dark glass, accent bar on the leader side, brackets on the corners.
        drawRect(Color.Black.copy(alpha = if (on) 0.78f else 0.56f), Offset(r.left, r.top), Size(r.width, r.height))
        drawRect(line.copy(alpha = if (on) 0.9f else 0.5f), Offset(r.left, if (c.slot < 4) r.bottom - 2 * dp else r.top), Size(r.width, 2 * dp))
        val arm = 6 * dp
        val bracket = line.copy(alpha = if (on) 1f else 0.85f)
        val w = 1.4f * dp
        for ((cx, dx) in listOf(r.left to 1f, r.right to -1f)) {
            for ((cy, dy) in listOf(r.top to 1f, r.bottom to -1f)) {
                drawLine(bracket, Offset(cx, cy), Offset(cx + dx * arm, cy), w)
                drawLine(bracket, Offset(cx, cy), Offset(cx, cy + dy * arm), w)
            }
        }
        drawCircle(levelColor(c.level), 2.5f * dp, Offset(r.right - 7 * dp, r.top + 8 * dp))

        drawIntoCanvas { cv ->
            val x = r.left + 7 * dp
            val compact = r.height < 34 * dp
            val title = c.title.take(((r.width - 20 * dp) / titlePaint.measureText("M")).toInt().coerceAtLeast(3))
            cv.nativeCanvas.drawText(title, x, r.top + (if (compact) 9f else 11f) * dp, titlePaint)
            valuePaint.textSize = (if (compact) 11f else 13f) * dp
            var text = c.value
            while (text.length > 2 && valuePaint.measureText(text) > r.width - 12 * dp) text = text.dropLast(1)
            cv.nativeCanvas.drawText(text, x, r.top + (if (compact) 21f else 25f) * dp, valuePaint)
            val subText = c.sub
            if (!compact && subText != null) {
                var sub: String = subText
                while (sub.length > 2 && subPaint.measureText(sub) > r.width - 12 * dp) sub = sub.dropLast(1)
                cv.nativeCanvas.drawText(sub, x, r.top + 35f * dp, subPaint)
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
    // Lines that recede: spaced like a floor seen at a low angle (closer together towards the horizon).
    for (i in 0..6) {
        val t = i / 6f
        val y = top + (bottom - top) * t * t
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, line.copy(alpha = 0.10f + 0.28f * t), Color.Transparent)), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
    // Lines that converge on the vanishing point.
    for (i in -7..7) {
        val xb = cx + i * size.width * 0.16f
        val xt = cx + (xb - cx) * ((top - vanishY) / (bottom - vanishY))
        drawLine(
            Brush.verticalGradient(listOf(line.copy(alpha = 0.04f), line.copy(alpha = 0.30f)), top, bottom),
            Offset(xt, top), Offset(xb, bottom), 1.dp.toPx()
        )
    }
}

/** The bigger info card that opens when a callout is tapped: value, session range and what the number means. */
@Composable
fun CalloutCard(detail: CalloutDetail, color: Color, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth(0.9f)
            .clip(shape)
            .background(KittBlack.copy(alpha = 0.94f))
            .skinFrame(shape, color, selected = true)
            .clickable(onClick = onClose)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(detail.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail.value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = DashWhite, maxLines = 1, softWrap = false)
        }
        for ((label, value) in detail.rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = DashGrey, maxLines = 1)
                Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DashAmber.takeIf { detail.level == ReadoutLevel.Warn } ?: DashWhite, maxLines = 1)
            }
        }
        Text(detail.about, style = MaterialTheme.typography.bodySmall, color = DashWhite.copy(alpha = 0.85f))
        Text("TAP TO CLOSE", style = MaterialTheme.typography.labelSmall, color = DashGrey)
    }
}
