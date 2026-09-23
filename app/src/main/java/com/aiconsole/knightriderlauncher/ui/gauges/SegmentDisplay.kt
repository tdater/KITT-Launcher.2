package com.aiconsole.knightriderlauncher.ui.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber

private const val A = 1
private const val B = 2
private const val C = 4
private const val D = 8
private const val E = 16
private const val F = 32
private const val G = 64

private val GLYPHS = mapOf(
    '0' to (A or B or C or D or E or F),
    '1' to (B or C),
    '2' to (A or B or G or E or D),
    '3' to (A or B or G or C or D),
    '4' to (F or G or B or C),
    '5' to (A or F or G or C or D),
    '6' to (A or F or G or E or D or C),
    '7' to (A or B or C),
    '8' to 127,
    '9' to (A or B or C or D or F or G),
    '-' to G,
    'E' to (A or F or G or E or D),
    'r' to (E or G),
    'L' to (F or E or D),
    'H' to (F or E or G or B or C),
    'P' to (A or B or F or G or E),
    ' ' to 0
)

/**
 * KITT-style seven-segment LED readout, with the unlit "ghost" segments faintly visible
 * like a real display. Digits are right-aligned; [cells] fixes the width so values don't
 * jump around as they change. A '.' attaches a decimal point to the previous digit.
 */
@Composable
fun SegmentDisplay(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DashAmber,
    cells: Int = 0
) {
    Canvas(modifier) {
        val glyphs = mutableListOf<Pair<Char, Boolean>>()
        text.forEach { ch ->
            if (ch == '.' && glyphs.isNotEmpty()) {
                glyphs[glyphs.lastIndex] = glyphs.last().first to true
            } else {
                glyphs += ch to false
            }
        }
        val count = maxOf(cells, glyphs.size, 1)
        while (glyphs.size < count) glyphs.add(0, ' ' to false)

        // Cell aspect ~0.55 plus spacing; fit to both width and height.
        val cellHeight = minOf(size.height, size.width / (count * 0.72f))
        val cellWidth = cellHeight * 0.55f
        val spacing = cellHeight * 0.17f
        val totalWidth = count * cellWidth + (count - 1) * spacing
        var x = size.width - totalWidth
        val y = (size.height - cellHeight) / 2
        glyphs.forEach { (ch, dot) ->
            drawGlyph(GLYPHS[ch] ?: 0, dot, Offset(x, y), cellWidth, cellHeight, color)
            x += cellWidth + spacing
        }
    }
}

private fun DrawScope.drawGlyph(mask: Int, dot: Boolean, origin: Offset, w: Float, h: Float, color: Color) {
    val t = w * 0.17f
    val gap = t * 0.25f
    val radius = CornerRadius(t / 2)
    val ghost = color.copy(alpha = 0.08f)
    fun seg(bit: Int, x: Float, y: Float, sw: Float, sh: Float) {
        drawRoundRect(if (mask and bit != 0) color else ghost, origin + Offset(x, y), Size(sw, sh), radius)
    }
    val half = h / 2
    seg(A, t + gap, 0f, w - 2 * (t + gap), t)
    seg(G, t + gap, half - t / 2, w - 2 * (t + gap), t)
    seg(D, t + gap, h - t, w - 2 * (t + gap), t)
    seg(F, 0f, t / 2 + gap, t, half - t - 2 * gap)
    seg(B, w - t, t / 2 + gap, t, half - t - 2 * gap)
    seg(E, 0f, half + t / 2 + gap, t, half - t - 2 * gap)
    seg(C, w - t, half + t / 2 + gap, t, half - t - 2 * gap)
    if (dot) drawCircle(color, t * 0.6f, origin + Offset(w + t * 0.55f, h - t / 2))
}
