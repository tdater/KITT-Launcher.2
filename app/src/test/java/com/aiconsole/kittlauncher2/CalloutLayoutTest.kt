package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.components.Callout
import com.aiconsole.kittlauncher2.ui.components.CalloutLayout
import com.aiconsole.kittlauncher2.ui.components.ReadoutLevel
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

    @Test fun assignSlotsOrdersEachRowLeftToRightByAnchorX() {
        val callouts = listOf(
            Callout("engine", "ENG", "--", null, ReadoutLevel.Unknown, 0, "engine"),
            Callout("speed", "SPD", "--", null, ReadoutLevel.Unknown, 1, "roof"),
            Callout("battery", "BAT", "--", null, ReadoutLevel.Unknown, 2, "battery"),
            Callout("fuel", "FUEL", "--", null, ReadoutLevel.Unknown, 3, "fuel"),
            Callout("tire0", "FL", "--", null, ReadoutLevel.Unknown, 4, "w0"),
            Callout("tire1", "FR", "--", null, ReadoutLevel.Unknown, 5, "w1"),
            Callout("tire2", "RL", "--", null, ReadoutLevel.Unknown, 6, "w2"),
            Callout("tire3", "RR", "--", null, ReadoutLevel.Unknown, 7, "w3"),
        )
        val xs = mapOf(
            "fuel" to 0.20f,
            "roof" to 0.45f,
            "engine" to 0.72f,
            "battery" to 0.81f,
            "w2" to 0.18f,
            "w3" to 0.35f,
            "w0" to 0.68f,
            "w1" to 0.84f,
        )
        CalloutLayout.assignSlots(callouts) { xs.getValue(it) }
        assertEquals(listOf("fuel", "speed", "engine", "battery"), callouts.filter { it.slot < 4 }.sortedBy { it.slot }.map { it.key })
        assertEquals(listOf("tire2", "tire3", "tire0", "tire1"), callouts.filter { it.slot >= 4 }.sortedBy { it.slot }.map { it.key })
    }

    @Test fun touchingVehicleAnchorSelectsClosestPidArea() {
        val callouts = listOf(
            Callout("engine", "ENG", "195°F", null, ReadoutLevel.Ok, 0, "engine"),
            Callout("battery", "BAT", "14.2V", null, ReadoutLevel.Ok, 2, "battery"),
            Callout("tire0", "FL", "33 PSI", null, ReadoutLevel.Ok, 4, "w0")
        )
        val pts = mapOf(
            "engine" to androidx.compose.ui.geometry.Offset(220f, 110f),
            "battery" to androidx.compose.ui.geometry.Offset(260f, 105f),
            "w0" to androidx.compose.ui.geometry.Offset(190f, 145f)
        )
        assertEquals("engine", CalloutLayout.hitAnchor(224f, 112f, callouts, { pts.getValue(it) }, maxRadius = 28f))
        assertEquals("tire0", CalloutLayout.hitAnchor(185f, 142f, callouts, { pts.getValue(it) }, maxRadius = 28f))
        org.junit.Assert.assertNull(CalloutLayout.hitAnchor(50f, 50f, callouts, { pts.getValue(it) }, maxRadius = 28f))
    }
}
