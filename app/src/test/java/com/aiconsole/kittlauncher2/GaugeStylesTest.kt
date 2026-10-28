package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.obd.GaugeStyle
import com.aiconsole.kittlauncher2.ui.gauges.GaugeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GaugeStylesTest {

    @Test
    fun storedStyleNamesStillLoad() {
        // GaugeStore persists the enum name; existing dashboards must keep their faces.
        listOf("DIAL", "BAR", "DIGITAL", "GRAPH").forEach { assertEquals(it, GaugeStyle.valueOf(it).name) }
        assertEquals(GaugeStyle.DIAL, GaugeStyle.entries.first())
    }

    @Test
    fun everyStyleHasAUniqueLabelAndBlurb() {
        val labels = GaugeStyle.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(GaugeStyle.entries.all { it.blurb.isNotBlank() })
        assertEquals(11, GaugeStyle.entries.size)
    }

    @Test
    fun fractionClampsAndHandlesMissingValues() {
        assertEquals(0f, GaugeScale.fraction(null, 0.0, 100.0), 0f)
        assertEquals(0f, GaugeScale.fraction(Double.NaN, 0.0, 100.0), 0f)
        assertEquals(0f, GaugeScale.fraction(50.0, 100.0, 100.0), 0f)
        assertEquals(0.25f, GaugeScale.fraction(25.0, 0.0, 100.0), 0.0001f)
        assertEquals(1f, GaugeScale.fraction(250.0, 0.0, 100.0), 0f)
        assertEquals(0f, GaugeScale.fraction(-40.0, 0.0, 100.0), 0f)
    }

    @Test
    fun niceStepsAreOneTwoFive() {
        assertEquals(1000.0, GaugeScale.niceStep(8000.0, 8), 1e-9)
        assertEquals(2000.0, GaugeScale.niceStep(8000.0, 6), 1e-9)
        assertEquals(50.0, GaugeScale.niceStep(255.0, 6), 1e-9)
        assertEquals(0.5, GaugeScale.niceStep(3.0, 6), 1e-9)
        assertEquals(1.0, GaugeScale.niceStep(0.0, 6), 0.0)
    }

    @Test
    fun ticksLandOnStepMultiplesInsideTheRange() {
        assertEquals(listOf(0.0, 50.0, 100.0, 150.0, 200.0, 250.0), GaugeScale.ticks(0.0, 255.0, 50.0))
        assertEquals(listOf(-40.0, -20.0, 0.0, 20.0), GaugeScale.ticks(-40.0, 20.0, 20.0))
        assertEquals(listOf(20.0, 40.0), GaugeScale.ticks(13.0, 47.0, 20.0))
        assertTrue(GaugeScale.ticks(10.0, 0.0, 5.0).isEmpty())
        assertTrue(GaugeScale.ticks(0.0, 1e9, 1e-3).size <= 200)
    }

    @Test
    fun labelsUseThousandsForTachometers() {
        assertEquals(1000.0, GaugeScale.labelDivisor(8000.0), 0.0)
        assertEquals(1.0, GaugeScale.labelDivisor(255.0), 0.0)
        assertEquals("6", GaugeScale.label(6000.0, 1000.0))
        assertEquals("2.5", GaugeScale.label(2500.0, 1000.0))
        assertEquals("-40", GaugeScale.label(-40.0))
        assertTrue(GaugeScale.isMultiple(150.0, 50.0))
        assertFalse(GaugeScale.isMultiple(125.0, 50.0))
    }

    @Test
    fun needleSweepsFromBottomLeftToBottomRight() {
        assertEquals(135f, GaugeScale.needleAngle(0f), 0f)
        assertEquals(270f, GaugeScale.needleAngle(0.5f), 0f)
        assertEquals(405f, GaugeScale.needleAngle(1f), 0f)
        assertEquals(405f, GaugeScale.needleAngle(3f), 0f)
    }

    @Test
    fun skylineRisesAndLightsInOrder() {
        val h = GaugeScale.skylineHeights(16)
        assertEquals(16, h.size)
        assertEquals(0.25f, h.first(), 1e-6f)
        assertEquals(1f, h.last(), 1e-6f)
        assertTrue(h.zipWithNext().all { (a, b) -> b > a })
        assertTrue(GaugeScale.skylineHeights(0).isEmpty())
        assertEquals(0, GaugeScale.lit(0f, 16))
        assertEquals(8, GaugeScale.lit(0.5f, 16))
        assertEquals(16, GaugeScale.lit(2f, 16))
    }

    @Test
    fun cellsAttachDecimalPointsAndRightAlign() {
        assertEquals(
            listOf(' ' to false, '1' to false, '2' to true, '6' to false),
            GaugeScale.cells("12.6", 4)
        )
        assertEquals(listOf('-' to false, '-' to false), GaugeScale.cells("--", 2))
        assertEquals(5, GaugeScale.cells("12345", 4).size)
        assertEquals(listOf('.' to false), GaugeScale.cells(".", 1))
    }
}
