package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.ui.theme.NightModeRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightModeRulesTest {

    @Test
    fun darkBelowThreshold() {
        assertTrue(NightModeRules.nextDark(wasDark = false, lux = 2f))
    }

    @Test
    fun lightAboveThreshold() {
        assertFalse(NightModeRules.nextDark(wasDark = true, lux = 200f))
    }

    @Test
    fun hysteresisKeepsPreviousStateInBetween() {
        assertTrue(NightModeRules.nextDark(wasDark = true, lux = 20f))
        assertFalse(NightModeRules.nextDark(wasDark = false, lux = 20f))
    }

    @Test
    fun nightHoursByClock() {
        assertTrue(NightModeRules.isNightHour(19))
        assertTrue(NightModeRules.isNightHour(2))
        assertFalse(NightModeRules.isNightHour(7))
        assertFalse(NightModeRules.isNightHour(18))
    }
}
