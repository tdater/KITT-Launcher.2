package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.components.CockpitSkin
import com.aiconsole.kittlauncher2.ui.components.HoloFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoloFxTest {

    @Test
    fun motesStayInsideTheBeamAndAreRepeatable() {
        for (i in 0 until HoloFx.MOTES) {
            for (phase in listOf(0f, 0.37f, 0.99f, 5.2f, -0.4f)) {
                val m = HoloFx.mote(i, phase)
                assertTrue(m.x in -1f..1f)
                assertTrue(m.rise in 0f..1f)
                assertTrue(m.alpha in 0f..0.56f)
                assertEquals(m, HoloFx.mote(i, phase))
            }
        }
    }

    @Test
    fun moteFadesAtTheBaseAndTopOfTheBeam() {
        val alphas = (0..100).map { HoloFx.mote(3, it / 100f) }
        assertTrue(alphas.minOf { it.alpha } < 0.05f)
        assertTrue(alphas.maxOf { it.alpha } > 0.3f)
    }

    @Test
    fun flickerIsMostlySteadyWithBriefDips() {
        val values = (0 until 1000).map { HoloFx.flicker(it / 1000f) }
        assertTrue(values.all { it == 1f || it == 0.82f })
        val dips = values.count { it < 1f }
        assertTrue(dips in 1..100)
    }

    @Test
    fun tachArcWarnsNearRedline() {
        assertEquals(0, HoloFx.rpmWarn(0.5f))
        assertEquals(1, HoloFx.rpmWarn(0.85f))
        assertEquals(2, HoloFx.rpmWarn(0.95f))
    }

    @Test
    fun ellipsePointSitsOnTheFloorEllipse() {
        val p = HoloFx.ellipsePoint(100f, 50f, 40f, 10f, 0f)
        assertEquals(140f, p.x, 0.001f)
        assertEquals(50f, p.y, 0.001f)
        val q = HoloFx.ellipsePoint(100f, 50f, 40f, 10f, (Math.PI / 2).toFloat())
        assertEquals(100f, q.x, 0.001f)
        assertEquals(60f, q.y, 0.001f)
    }

    @Test
    fun everyThemeHasItsOwnStyle() {
        val styles = CockpitSkin.entries.associateWith { HoloFx.style(it) }
        assertEquals(CockpitSkin.entries.size, styles.size)
        assertEquals(88, styles.getValue(CockpitSkin.Future).ticks)
        assertTrue(styles.values.all { it.ticks in 36..120 && it.scanlineAlpha in 0.01f..0.2f })
        assertTrue(styles.getValue(CockpitSkin.Matrix).scanlineAlpha > styles.getValue(CockpitSkin.Kitt).scanlineAlpha)
    }
}
