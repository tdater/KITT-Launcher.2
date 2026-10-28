package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.components.Turntable
import com.aiconsole.kittlauncher2.ui.gauges.PeakHold
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class TurntableAndPeakTest {

    private val twoPi = (2 * PI).toFloat()

    @Test
    fun blendPicksNeighbouringFramesAndTheMixBetweenThem() {
        assertEquals(Turntable.Blend(0, 1, 0f), Turntable.blend(0f, 72))
        val b = Turntable.blend(twoPi * 10.5f / 72f, 72)
        assertEquals(10, b.from)
        assertEquals(11, b.to)
        assertEquals(0.5f, b.mix, 0.001f)
    }

    @Test
    fun lastFrameBlendsBackIntoTheFirst() {
        val b = Turntable.blend(twoPi * 71.25f / 72f, 72)
        assertEquals(71, b.from)
        assertEquals(0, b.to)
        assertEquals(0.25f, b.mix, 0.001f)
    }

    @Test
    fun yawWrapsAndNegativeYawIsHandled() {
        assertEquals(Turntable.blend(0.3f, 36), Turntable.blend(0.3f + twoPi, 36))
        assertEquals(35, Turntable.blend(-0.01f, 36).from)
        assertEquals(Turntable.Blend(0, 0, 0f), Turntable.blend(1f, 1))
    }

    @Test
    fun advanceIsTimeBasedAndIgnoresLongStalls() {
        assertEquals(Turntable.SPEED * 0.2f, Turntable.advance(0f, 200), 0.0001f)
        assertEquals(Turntable.SPEED * 0.033f * 2, Turntable.advance(Turntable.advance(0f, 33), 33), 0.0001f)
        // A 5 s stall (screen off, page hidden) moves no further than a quarter-second tick.
        assertEquals(Turntable.SPEED * 0.25f, Turntable.advance(0f, 5000), 0.0001f)
        assertEquals(0f, Turntable.advance(0f, -50), 0f)
    }

    @Test
    fun preloadStartsAtTheVisibleFrameAndCoversAll() {
        val order = Turntable.preloadOrder(70, 72)
        assertEquals(listOf(70, 71, 0, 1), order.take(4))
        assertEquals((0 until 72).toSet(), order.toSet())
        assertEquals(emptyList<Int>(), Turntable.preloadOrder(3, 0))
    }

    @Test
    fun peakHoldsTheHighestValueThisDrive() {
        val peaks = PeakHold()
        assertEquals(50.0, peaks.update("g", 50.0, 1_000), 0.0)
        assertEquals(80.0, peaks.update("g", 80.0, 2_000), 0.0)
        assertEquals(80.0, peaks.update("g", 30.0, 3_000), 0.0)
        assertEquals(10.0, peaks.update("other", 10.0, 3_000), 0.0)
    }

    @Test
    fun peakResetsAfterTheIgnitionHasBeenOff() {
        val peaks = PeakHold()
        peaks.update("g", 120.0, 0)
        assertEquals(120.0, peaks.update("g", 20.0, 60_000), 0.0)
        assertEquals(15.0, peaks.update("g", 15.0, 60_000 + PeakHold.NEW_DRIVE_MS + 1), 0.0)
        peaks.reset("g")
        assertEquals(5.0, peaks.update("g", 5.0, 200_000), 0.0)
    }
}
