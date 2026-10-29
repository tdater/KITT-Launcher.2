package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.components.CarReadouts
import com.aiconsole.kittlauncher2.ui.components.ReadoutLevel
import com.aiconsole.kittlauncher2.ui.components.ReadoutStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarReadoutsTest {
    @Test fun missingValueShowsDashes() {
        for (r in CarReadouts.ALL) assertEquals("--", CarReadouts.text(r.key, null, true))
    }

    @Test fun speedAndTemperatureConvertToImperial() {
        assertEquals("62 mph", CarReadouts.text("speed", 100.0, false))
        assertEquals("100 km/h", CarReadouts.text("speed", 100.0, true))
        assertEquals("104°F", CarReadouts.text("intake", 40.0, false))
        assertEquals("40°C", CarReadouts.text("intake", 40.0, true))
    }

    @Test fun fuelUseConvertsToGallonsPerHour() {
        assertEquals("3.8 L/h", CarReadouts.text("flow", 3.785, true))
        assertEquals("1.00 gal/h", CarReadouts.text("flow", 3.78541, false))
    }

    @Test fun chipsKeepWordUnitsInTheLabel() {
        val speed = CarReadouts.byKey("speed")!!
        assertEquals("MPH", CarReadouts.chipLabel(speed, false))
        assertEquals("L/H", CarReadouts.chipLabel(CarReadouts.byKey("flow")!!, true))
        assertEquals("THRTL", CarReadouts.chipLabel(CarReadouts.byKey("throttle")!!, true))
        assertEquals("LOAD",CarReadouts.chipLabel(CarReadouts.byKey("load")!!, true))
        assertEquals("62", CarReadouts.chipValue("speed", 100.0, false))
        assertEquals("104°F", CarReadouts.chipValue("intake", 40.0, false))
        assertEquals("43%", CarReadouts.chipValue("load", 42.6, true))
        assertEquals("--", CarReadouts.chipValue("flow", null, true))
    }

    @Test fun percentagesAreRounded() {
        assertEquals("43%", CarReadouts.text("load", 42.6, true))
    }

    @Test fun levelsWarnAtTheThresholds() {
        assertEquals(ReadoutLevel.Unknown, CarReadouts.level("load", null))
        assertEquals(ReadoutLevel.Ok, CarReadouts.level("load", 60.0))
        assertEquals(ReadoutLevel.Warn, CarReadouts.level("load", 90.0))
        assertEquals(ReadoutLevel.Bad, CarReadouts.level("load", 99.0))
        assertEquals(ReadoutLevel.Warn, CarReadouts.level("fuel", 20.0))
        assertEquals(ReadoutLevel.Bad, CarReadouts.level("fuel", 5.0))
        assertEquals(ReadoutLevel.Bad, CarReadouts.level("intake", 80.0))
    }

    @Test fun everyReadoutHasUniqueKeyAndPid() {
        assertEquals(CarReadouts.ALL.size, CarReadouts.ALL.map { it.key }.toSet().size)
        assertEquals(CarReadouts.ALL.size, CarReadouts.PIDS.size)
        assertTrue(CarReadouts.byKey("speed") != null)
    }

    @Test fun statsTrackLowHighAverage() {
        val s = ReadoutStats()
        assertTrue(s.average.isNaN())
        s.add(10.0); s.add(30.0); s.add(20.0)
        assertEquals(10.0, s.min, 0.0)
        assertEquals(30.0, s.max, 0.0)
        assertEquals(20.0, s.average, 1e-9)
        assertEquals(3, s.count)
    }
}
