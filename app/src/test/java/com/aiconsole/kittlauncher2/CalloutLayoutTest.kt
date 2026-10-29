package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.components.CalloutLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalloutLayoutTest {
    private val sizes = listOf(
        Triple(365f, 110f, 1f),    // a compact film-theme panel
        Triple(365f, 170f, 1f),    // the landscape Drive pane
        Triple(580f, 330f, 1f),    // the portrait pane
        Triple(780f, 340f, 2f)     // the same at twice the density
    )

    @Test fun eightBoxesFourOnTopFourBelow() {
        for ((w, h, d) in sizes) {
            val b = CalloutLayout.boxes(w * d, h * d, d)
            assertEquals(CalloutLayout.SLOTS, b.rects.size)
            assertTrue(b.rects.take(4).all { it.bottom <= b.bandTop })
            assertTrue(b.rects.drop(4).all { it.top >= b.bandBottom })
        }
    }

    @Test fun boxesStayInsideTheCanvasAndNeverOverlap() {
        for ((w, h, d) in sizes) {
            val b = CalloutLayout.boxes(w * d, h * d, d)
            for (r in b.rects) {
                assertTrue(r.left >= 0f && r.top >= 0f && r.right <= w * d && r.bottom <= h * d)
                assertTrue(r.width > 0f && r.height > 0f)
            }
            for (i in b.rects.indices) for (j in i + 1 until b.rects.size) assertFalse("boxes $i and $j overlap at ${w}x$h", b.rects[i].overlaps(b.rects[j]))
        }
    }

    @Test fun theVehicleKeepsARoomyBandBetweenTheRows() {
        val b = CalloutLayout.boxes(365f, 170f, 1f)
        assertTrue("band ${b.bandHeight}", b.bandHeight >= 70f)
        val tall = CalloutLayout.boxes(580f, 330f, 1f)
        assertTrue(tall.bandHeight > 170f)
    }

    @Test fun aTouchFindsTheBoxUnderIt() {
        val b = CalloutLayout.boxes(365f, 170f, 1f)
        for ((i, r) in b.rects.withIndex()) assertEquals(i, b.hit((r.left + r.right) / 2, (r.top + r.bottom) / 2))
        assertEquals(-1, b.hit(b.rects[0].left - 1f, (b.bandTop + b.bandBottom) / 2))
    }
}
