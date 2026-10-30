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

    @Test fun bottomCalloutsShowTpmsWhenAvailableAndFallbackToMode01WhenAbsent() {
        // 1) When TPMS is reported, show the 4 tire pressures
        val tpmsBoxes = CarReadouts.bottomCallouts(
            tirePrefix = "TPMS",
            tiresKpa = listOf(220.0, 220.0, 214.0, 160.0),
            metric = false,
            load = 35.0,
            throttle = 18.0,
            intakeC = 28.0,
            burnLph = 4.2,
            rpm = 1850.0
        )
        assertEquals(listOf("TPMS FL", "TPMS FR", "TPMS RL", "TPMS RR"), tpmsBoxes.map { it.title })
        assertEquals("32 PSI", tpmsBoxes[0].value)
        assertEquals("OK", tpmsBoxes[0].sub)
        assertEquals(ReadoutLevel.Ok, tpmsBoxes[0].level)
        assertEquals("23 PSI", tpmsBoxes[3].value)
        assertEquals("LOW", tpmsBoxes[3].sub)
        assertEquals(ReadoutLevel.Bad, tpmsBoxes[3].level)

        // 2) When no TPMS is reported but Mode 01 OBD is streaming, fall back to live Mode 01 boxes
        val fallbackBoxes = CarReadouts.bottomCallouts(
            tirePrefix = "TPMS",
            tiresKpa = listOf(null, null, null, null),
            metric = false,
            load = 42.0,
            throttle = 21.0,
            intakeC = 30.0,
            burnLph = 3.78541,
            rpm = 1900.0
        )
        assertEquals(listOf("LOAD", "THROTTLE", "INTAKE", "BURN"), fallbackBoxes.map { it.title })
        assertEquals("42%", fallbackBoxes[0].value)
        assertEquals("21%", fallbackBoxes[1].value)
        assertEquals("86°F", fallbackBoxes[2].value)
        assertEquals("1.00 gal/h", fallbackBoxes[3].value)
    }
}
