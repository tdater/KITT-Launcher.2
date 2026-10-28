package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import kotlin.math.PI

/** The glyphs drawn on the driving panel's keys. No icon library: each is a few strokes. */
enum class KeyGlyph { Prev, Next, Play, Pause, VolDown, VolUp, Diag, Check, Route, Gear, Sliders }

/** A rectangle with its corners cut at 45 degrees, the shape of the console keys. */
fun chamfer(cut: Float) = GenericShape { size, _ ->
    val c = minOf(cut, size.width / 3f, size.height / 3f)
    moveTo(c, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, c)
    lineTo(size.width, size.height - c)
    lineTo(size.width - c, size.height)
    lineTo(c, size.height)
    lineTo(0f, size.height - c)
    lineTo(0f, c)
    close()
}

/**
 * A big, glanceable console key: chamfered body, a neon outline that glows while active, a drawn
 * icon and a small label under it. Used for the driving layout's media and volume controls.
 */
@Composable
fun IconKey(
    glyph: KeyGlyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    active: Boolean = false
) {
    val shape = chamfer(18f)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = if (active) 0.34f else 0.14f), KittBlack)))
            .drawBehind {
                // Neon outline along the chamfered edge, with a soft bloom while the key is on.
                val path = Path()
                val c = 18f
                path.moveTo(c, 0f); path.lineTo(size.width - c, 0f); path.lineTo(size.width, c); path.lineTo(size.width, size.height - c)
                path.lineTo(size.width - c, size.height); path.lineTo(c, size.height); path.lineTo(0f, size.height - c); path.lineTo(0f, c); path.close()
                if (active) drawPath(path, color.copy(alpha = 0.30f), style = Stroke(7.dp.toPx()))
                drawPath(path, color.copy(alpha = if (active) 1f else 0.75f), style = Stroke(if (active) 2.5.dp.toPx() else 1.6.dp.toPx()))
                // A highlight stripe along the top edge, like a lit bezel.
                drawLine(Color.White.copy(alpha = 0.16f), Offset(c + 4.dp.toPx(), 5.dp.toPx()), Offset(size.width - c - 4.dp.toPx(), 5.dp.toPx()), 1.5.dp.toPx())
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Canvas(Modifier.weight(1f).fillMaxSize()) { drawGlyph(glyph, if (active) Color.White else color, color) }
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else DashGrey, maxLines = 1, softWrap = false)
        }
    }
}

internal fun DrawScope.drawGlyph(g: KeyGlyph, core: Color, glow: Color) {
    val s = minOf(size.width, size.height) * 0.62f
    val c = Offset(size.width / 2f, size.height / 2f)
    val sw = (s * 0.11f).coerceAtLeast(2.dp.toPx())
    fun tri(cx: Float, half: Float, right: Boolean): Path = Path().apply {
        val d = if (right) 1f else -1f
        moveTo(cx - d * half, c.y - half)
        lineTo(cx + d * half, c.y)
        lineTo(cx - d * half, c.y + half)
        close()
    }
    fun shape(p: Path) {
        drawPath(p, glow.copy(alpha = 0.35f), style = Stroke(sw * 2.4f))
        drawPath(p, core)
    }
    when (g) {
        KeyGlyph.Play -> shape(tri(c.x, s * 0.42f, true))
        KeyGlyph.Pause -> for (dx in listOf(-0.16f, 0.16f)) {
            val r = Path().apply { addRect(androidx.compose.ui.geometry.Rect(c.x + s * dx - s * 0.09f, c.y - s * 0.38f, c.x + s * dx + s * 0.09f, c.y + s * 0.38f)) }
            shape(r)
        }
        KeyGlyph.Next -> {
            shape(tri(c.x - s * 0.24f, s * 0.30f, true))
            shape(tri(c.x + s * 0.24f, s * 0.30f, true))
        }
        KeyGlyph.Prev -> {
            shape(tri(c.x - s * 0.24f, s * 0.30f, false))
            shape(tri(c.x + s * 0.24f, s * 0.30f, false))
        }
        KeyGlyph.Diag -> {
            // A heartbeat trace: the engine health check.
            val p = Path().apply {
                moveTo(c.x - s * 0.5f, c.y); lineTo(c.x - s * 0.22f, c.y); lineTo(c.x - s * 0.1f, c.y - s * 0.38f)
                lineTo(c.x + s * 0.08f, c.y + s * 0.4f); lineTo(c.x + s * 0.2f, c.y); lineTo(c.x + s * 0.5f, c.y)
            }
            drawPath(p, glow.copy(alpha = 0.35f), style = Stroke(sw * 2.4f, cap = StrokeCap.Round))
            drawPath(p, core, style = Stroke(sw * 1.3f, cap = StrokeCap.Round))
        }
        KeyGlyph.Check -> {
            val p = Path().apply { moveTo(c.x - s * 0.36f, c.y + s * 0.02f); lineTo(c.x - s * 0.1f, c.y + s * 0.3f); lineTo(c.x + s * 0.38f, c.y - s * 0.3f) }
            drawPath(p, glow.copy(alpha = 0.35f), style = Stroke(sw * 2.6f, cap = StrokeCap.Round))
            drawPath(p, core, style = Stroke(sw * 1.5f, cap = StrokeCap.Round))
        }
        KeyGlyph.Route -> {
            // A road running away to the horizon, with its centre line.
            val road = Path().apply {
                moveTo(c.x - s * 0.5f, c.y + s * 0.42f); lineTo(c.x - s * 0.1f, c.y - s * 0.42f)
                lineTo(c.x + s * 0.1f, c.y - s * 0.42f); lineTo(c.x + s * 0.5f, c.y + s * 0.42f); close()
            }
            shape(road)
            for (k in 0..2) drawLine(Color.White.copy(alpha = 0.9f), Offset(c.x, c.y + s * (0.34f - k * 0.26f)), Offset(c.x, c.y + s * (0.22f - k * 0.26f)), sw, StrokeCap.Round)
        }
        KeyGlyph.Gear -> {
            val r = s * 0.28f
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                drawLine(core, Offset(c.x + kotlin.math.cos(a) * r, c.y + kotlin.math.sin(a) * r), Offset(c.x + kotlin.math.cos(a) * s * 0.46f, c.y + kotlin.math.sin(a) * s * 0.46f), sw * 1.8f, StrokeCap.Butt)
            }
            drawCircle(glow.copy(alpha = 0.35f), r + sw, c, style = Stroke(sw * 2.4f))
            drawCircle(core, r, c, style = Stroke(sw * 1.5f))
            drawCircle(core, s * 0.1f, c)
        }
        KeyGlyph.Sliders -> {
            for ((i, knob) in listOf(0.3f, 0.7f, 0.45f).withIndex()) {
                val y = c.y + (i - 1) * s * 0.34f
                drawLine(core, Offset(c.x - s * 0.46f, y), Offset(c.x + s * 0.46f, y), sw, StrokeCap.Round)
                drawCircle(core, s * 0.1f, Offset(c.x - s * 0.46f + s * 0.92f * knob, y))
            }
        }
        KeyGlyph.VolDown, KeyGlyph.VolUp -> {
            // A speaker with sound waves; the plus or minus beside it says which way.
            val sx = c.x - s * 0.30f
            val body = Path().apply {
                moveTo(sx, c.y - s * 0.14f); lineTo(sx + s * 0.16f, c.y - s * 0.14f); lineTo(sx + s * 0.40f, c.y - s * 0.36f)
                lineTo(sx + s * 0.40f, c.y + s * 0.36f); lineTo(sx + s * 0.16f, c.y + s * 0.14f); lineTo(sx, c.y + s * 0.14f); close()
            }
            shape(body)
            val waves = if (g == KeyGlyph.VolUp) 2 else 1
            for (k in 1..waves) {
                val r = s * (0.20f + 0.16f * k)
                val arc = Path().apply { arcTo(androidx.compose.ui.geometry.Rect(sx + s * 0.40f - r, c.y - r, sx + s * 0.40f + r, c.y + r), -45f, 90f, true) }
                drawPath(arc, glow.copy(alpha = 0.35f), style = Stroke(sw * 2.2f, cap = StrokeCap.Round))
                drawPath(arc, core, style = Stroke(sw, cap = StrokeCap.Round))
            }
            val px = c.x + s * 0.50f
            val py = c.y - s * 0.34f
            drawLine(core, Offset(px - s * 0.09f, py), Offset(px + s * 0.09f, py), sw, StrokeCap.Round)
            if (g == KeyGlyph.VolUp) drawLine(core, Offset(px, py - s * 0.09f), Offset(px, py + s * 0.09f), sw, StrokeCap.Round)
        }
    }
}

/**
 * The Holo dock key: a solid colour-filled, chamfered body with a drawn icon over a bold label, sized to be read at
 * a glance while driving. The dark glyph and text sit on the bright fill, so they stay readable in sun.
 */
@Composable
fun FilledKey(
    glyph: KeyGlyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color,
    active: Boolean = false
) {
    val shape = chamfer(14f)
    val ink = if (active) Color.White else KittBlack
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = if (active) 0.95f else 0.78f))))
            .drawBehind {
                if (active) drawRect(Color.White.copy(alpha = 0.14f))
                drawLine(Color.White.copy(alpha = 0.35f), Offset(14f, 4.dp.toPx()), Offset(size.width - 14f, 4.dp.toPx()), 1.5.dp.toPx())
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Canvas(Modifier.weight(1f).fillMaxSize()) { drawGlyph(glyph, ink, Color.Transparent) }
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = ink,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** A favourite app as a chamfered tile with a neon outline: icon on the left, name beside it. */
@Composable
fun AppKeyFrame(modifier: Modifier, color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = chamfer(14f)
    Box(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.16f), KittBlack)))
            .drawBehind {
                val path = Path()
                val c = 14f
                path.moveTo(c, 0f); path.lineTo(size.width - c, 0f); path.lineTo(size.width, c); path.lineTo(size.width, size.height - c)
                path.lineTo(size.width - c, size.height); path.lineTo(c, size.height); path.lineTo(0f, size.height - c); path.lineTo(0f, c); path.close()
                drawPath(path, color.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
                drawLine(color, Offset(c, size.height - 1.dp.toPx()), Offset(size.width * 0.35f, size.height - 1.dp.toPx()), 3.dp.toPx())
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}
