package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.ui.scanner.ScannerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftLightTest {

    @Test
    fun belowTheBandTheScannerSweepsNormally() {
        assertNull(ScannerEngine.shiftLevel(null, 5000))
        assertNull(ScannerEngine.shiftLevel(3000.0, 5000))
        assertNull(ScannerEngine.shiftLevel(3499.0, 5000))
        assertNull(ScannerEngine.shiftLevel(4000.0, 0))
    }

    @Test
    fun levelRisesThroughTheBandAndCapsAtTheShiftPoint() {
        assertEquals(0f, ScannerEngine.shiftLevel(3500.0, 5000)!!, 0.001f)
        assertEquals(0.5f, ScannerEngine.shiftLevel(4250.0, 5000)!!, 0.001f)
        assertEquals(1f, ScannerEngine.shiftLevel(5000.0, 5000)!!, 0.001f)
        assertEquals(1f, ScannerEngine.shiftLevel(6200.0, 5000)!!, 0.001f)
    }

    @Test
    fun lampsFillFromBothEndsInward() {
        val lamps = 8
        // Entering the band lights just the outer pair.
        val start = (0 until lamps).map { ScannerEngine.shiftLampLit(it, lamps, 0f) }
        assertEquals(listOf(true, false, false, false, false, false, false, true), start)
        // Halfway: two per side.
        val half = (0 until lamps).map { ScannerEngine.shiftLampLit(it, lamps, 0.5f) }
        assertEquals(listOf(true, true, false, false, false, false, true, true), half)
        // Full: everything.
        assertTrue((0 until lamps).all { ScannerEngine.shiftLampLit(it, lamps, 1f) })
    }

    @Test
    fun flashTogglesEverySixteenthOfASecond() {
        assertTrue(ScannerEngine.shiftFlashOn(0L))
        assertFalse(ScannerEngine.shiftFlashOn(62_500_000L))
        assertTrue(ScannerEngine.shiftFlashOn(125_000_000L))
    }
}
