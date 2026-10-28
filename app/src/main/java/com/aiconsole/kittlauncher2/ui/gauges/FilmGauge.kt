package com.aiconsole.kittlauncher2.ui.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.HudStyle
import com.aiconsole.kittlauncher2.ui.theme.KittCharcoal
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import com.aiconsole.kittlauncher2.ui.theme.ThemeState
import com.aiconsole.kittlauncher2.ui.theme.hud

/**
 * A face that follows the film theme: KITT's voice-box columns, the Terminator's targeting reticle,
 * RoboCop's visor band, Matrix code columns, or a Back to the Future time-circuits plate.
 */
@Composable
internal fun ColumnScope.FilmBody(face: GaugeFace) {
    val measurer = rememberTextMeasurer()
    val hud = ThemeState.theme.hud
    val line = if (face.warning) face.accent else ScannerRed
    val green = LedGreen
    val amber = DashAmber
    val grey = DashGrey
    val white = DashWhite
    val plate = KittCharcoal
    val name = (face.def?.shortName ?: "").uppercase()
    Canvas(Modifier.fillMaxWidth().weight(1f)) {
        when (hud) {
            HudStyle.Terminator -> reticle(measurer, face, line, name)
            HudStyle.RoboCop -> visor(measurer, face, line, white)
            HudStyle.Matrix -> code(measurer, face, if (face.warning) face.accent else green)
            HudStyle.Future -> timeCircuits(measurer, face, plate, amber, grey, name)
            null -> voiceBox(measurer, face, line)
        }
    }
}

/** KITT's voice modulator: three columns of lamps, the middle one tallest, lit to the value. */
private fun DrawScope.voiceBox(m: TextMeasurer, face: GaugeFace, line: Color) {
    val readout = size.width * 0.4f
    val boxW = size.width - readout
    val colW = boxW / 3.6f
    val rows = 12
    val gap = size.height / rows * 0.28f
    val cell = size.height / rows - gap
    for (c in 0 until 3) {
        val x = c * colW * 1.25f
        val scale = if (c == 1) 1f else 0.7f
        val lit = GaugeScale.lit(face.fraction * scale, rows)
        for (r in 0 until rows) {
            // Lamps light outward from the middle row, like KITT talking.
            val fromMid = kotlin.math.abs(r - (rows - 1) / 2f) * 2f
            val on = fromMid < lit
            drawRect(line.copy(alpha = if (on) 1f else 0.1f), Offset(x, r * (cell + gap)), Size(colW, cell))
        }
    }
    text(m, face.text, Offset(boxW + readout / 2f, size.height / 2f), size.height * 0.3f, face.accent, glow = size.height * 0.08f)
}

/** Terminator vision: a red targeting circle, crosshair, sweep arc and a machine readout. */
private fun DrawScope.reticle(m: TextMeasurer, face: GaugeFace, line: Color, name: String) {
    val c = Offset(size.width * 0.32f, size.height / 2f)
    val r = minOf(size.width * 0.28f, size.height * 0.45f)
    drawCircle(line.copy(alpha = 0.7f), r, c, style = Stroke(1.5f))
    drawCircle(line.copy(alpha = 0.35f), r * 0.55f, c, style = Stroke(1f))
    drawArc(line, -90f, 360f * face.fraction, false, Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = Stroke(r * 0.12f))
    drawLine(line, Offset(c.x - r * 1.2f, c.y), Offset(c.x - r * 0.3f, c.y), 1.5f)
    drawLine(line, Offset(c.x + r * 0.3f, c.y), Offset(c.x + r * 1.2f, c.y), 1.5f)
    drawLine(line, Offset(c.x, c.y - r * 1.1f), Offset(c.x, c.y - r * 0.3f), 1.5f)
    drawLine(line, Offset(c.x, c.y + r * 0.3f), Offset(c.x, c.y + r * 1.1f), 1.5f)
    val x = size.width * 0.78f
    text(m, "TARGET", Offset(x, size.height * 0.2f), size.height * 0.12f, line.copy(alpha = 0.8f), bold = false)
    text(m, name.take(8), Offset(x, size.height * 0.36f), size.height * 0.12f, line.copy(alpha = 0.8f), bold = false)
    text(m, face.text, Offset(x, size.height * 0.66f), size.height * 0.28f, line, glow = size.height * 0.06f)
}

/** RoboCop's visor: a dark band with a scanning bar at the value and the reading boxed in brackets. */
private fun DrawScope.visor(m: TextMeasurer, face: GaugeFace, line: Color, white: Color) {
    val top = size.height * 0.52f
    val bandH = size.height * 0.3f
    drawRect(Color.Black, Offset(0f, top), Size(size.width, bandH))
    drawRect(line.copy(alpha = 0.6f), Offset(0f, top), Size(size.width, bandH), style = Stroke(1.5f))
    // Hatching across the band.
    var x = 0f
    while (x < size.width) {
        drawLine(line.copy(alpha = 0.12f), Offset(x, top), Offset(x + bandH, top + bandH), 1f)
        x += bandH * 0.4f
    }
    val sx = size.width * face.fraction
    drawRect(line.copy(alpha = 0.25f), Offset(0f, top), Size(sx, bandH))
    drawLine(white, Offset(sx, top - 4f), Offset(sx, top + bandH + 4f), 3f)
    text(m, face.text, Offset(size.width / 2f, size.height * 0.24f), size.height * 0.34f, white, glow = size.height * 0.05f)
    val b = size.height * 0.14f
    for (dir in listOf(-1f, 1f)) {
        val bx = size.width / 2f + dir * size.width * 0.3f
        drawLine(line, Offset(bx, size.height * 0.06f), Offset(bx, size.height * 0.42f), 2f)
        drawLine(line, Offset(bx, size.height * 0.06f), Offset(bx - dir * b, size.height * 0.06f), 2f)
        drawLine(line, Offset(bx, size.height * 0.42f), Offset(bx - dir * b, size.height * 0.42f), 2f)
    }
}

/** The Matrix: columns of code glyphs, filled to the value, with the reading in the stream. */
private fun DrawScope.code(m: TextMeasurer, face: GaugeFace, green: Color) {
    val glyphs = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄ0123456789"
    val cols = 12
    val rows = 7
    val cw = size.width / cols
    val ch = size.height / rows
    val lit = GaugeScale.lit(face.fraction, rows)
    for (c in 0 until cols) {
        val height = (lit + (c * 7 % 3) - 1).coerceIn(0, rows)
        for (r in 0 until rows) {
            val fromBottom = rows - 1 - r
            val on = fromBottom < height
            val head = fromBottom == height - 1
            val color = when {
                head -> Color(0xFFCFFFD8)
                on -> green.copy(alpha = 0.55f)
                else -> green.copy(alpha = 0.08f)
            }
            text(m, glyphs[(c * 31 + r * 17) % glyphs.length].toString(), Offset(c * cw + cw / 2f, r * ch + ch / 2f), ch * 0.7f, color, bold = false)
        }
    }
    drawRect(Color.Black.copy(alpha = 0.7f), Offset(size.width * 0.2f, size.height * 0.3f), Size(size.width * 0.6f, size.height * 0.4f))
    text(m, face.text, Offset(size.width / 2f, size.height / 2f), size.height * 0.3f, green, glow = size.height * 0.08f)
}

/** Back to the Future's time circuits: a label tape over a seven-segment window in a metal plate. */
private fun DrawScope.timeCircuits(m: TextMeasurer, face: GaugeFace, plate: Color, amber: Color, grey: Color, name: String) {
    val tapeH = size.height * 0.22f
    drawRect(Color(0xFFB71C1C), Offset(size.width * 0.2f, 0f), Size(size.width * 0.6f, tapeH))
    text(m, name.take(10), Offset(size.width / 2f, tapeH / 2f), tapeH * 0.62f, Color.White, bold = true)
    val top = tapeH + size.height * 0.06f
    val h = size.height - top
    drawRect(plate, Offset(0f, top), Size(size.width, h))
    drawRect(grey, Offset(0f, top), Size(size.width, h), style = Stroke(2f))
    // Rivets.
    for (x in listOf(size.width * 0.03f, size.width * 0.97f)) for (y in listOf(top + h * 0.12f, top + h * 0.88f)) drawCircle(grey, h * 0.04f, Offset(x, y))
    val win = Offset(size.width * 0.1f, top + h * 0.15f)
    val winSize = Size(size.width * 0.8f, h * 0.7f)
    drawRect(Color.Black, win, winSize)
    val digits = GaugeScale.cells(face.text, 4)
    val cellW = winSize.width / digits.size
    digits.forEachIndexed { i, (c, dot) ->
        val center = Offset(win.x + cellW * (i + 0.5f), win.y + winSize.height / 2f)
        text(m, "8", center, winSize.height * 0.8f, amber.copy(alpha = 0.08f))
        if (c != ' ') text(m, c.toString() + if (dot) "." else "", center, winSize.height * 0.8f, if (face.warning) face.accent else amber, glow = winSize.height * 0.15f)
    }
}
