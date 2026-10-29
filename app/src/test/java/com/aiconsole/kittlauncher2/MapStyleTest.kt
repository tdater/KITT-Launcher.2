package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.panes.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
    /** Applies the matrix to one pixel the way Android's ColorMatrix does (0..255, clamped). */
    private fun apply(m: FloatArray, r: Float, g: Float, b: Float): List<Float> =
        (0 until 3).map { row ->
            (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]).coerceIn(0f, 255f)
        }

    @Test fun matrixHasTwentyEntriesAndKeepsAlpha() {
        val m = MapStyle.matrix(1f, 0f, 0f)
        assertEquals(20, m.size)
        assertEquals(1f, m[18], 0f)
    }

    @Test fun paleLandGoesDarkAndRoadsGoBright() {
        val m = MapStyle.matrix(0f, 0.9f, 1f)
        val land = apply(m, 242f, 239f, 233f)
        val road = apply(m, 120f, 120f, 120f)
        assertTrue("land is dark", land.max() < 90f)
        assertTrue("road is brighter than land", road.sum() > land.sum() * 2)
    }

    @Test fun landKeepsTheThemeHue() {
        val red = apply(MapStyle.matrix(1f, 0.1f, 0.1f), 240f, 240f, 240f)
        assertTrue(red[0] > red[1] && red[0] > red[2])
        val green = apply(MapStyle.matrix(0.1f, 1f, 0.2f), 240f, 240f, 240f)
        assertTrue(green[1] > green[0] && green[1] > green[2])
    }

    @Test fun dimAccentsAreLiftedToFullBrightness() {
        val dim = MapStyle.matrix(0.4f, 0.04f, 0.04f)
        val full = MapStyle.matrix(1f, 0.1f, 0.1f)
        for (i in full.indices) assertEquals(full[i], dim[i], 0.01f)
        assertEquals(1f, MapStyle.bright(0.4f, 0.04f, 0.04f).first, 1e-6f)
    }

    @Test fun routeAvoidsTheMapsOwnHue() {
        val red = Triple(1f, 0.1f, 0.1f)
        val amber = Triple(1f, 0.7f, 0f)
        val green = Triple(0.1f, 1f, 0.3f)
        val cyan = Triple(0f, 0.9f, 1f)
        assertEquals(1, MapStyle.mostContrasting(red, listOf(red, green)))
        // An amber map must not get an amber route.
        assertEquals(1, MapStyle.mostContrasting(amber, listOf(amber, cyan)))
        assertEquals(0, MapStyle.mostContrasting(green, listOf(red, green)))
        assertEquals(-1f, MapStyle.hue(0.5f, 0.5f, 0.5f), 0f)
    }

    @Test fun brightestRoadsRunTowardsWhite() {
        val road = apply(MapStyle.matrix(1f, 0f, 0f), 20f, 20f, 20f)
        assertTrue("green and blue are lifted by the white-hot term", road[1] > 30f && road[2] > 30f)
    }
}
