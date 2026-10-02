package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.KittPersona
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KittPersonaTest {

    private fun prompt(style: PersonaStyle) = KittPersona.systemInstruction(style, "", webSearch = false, vehicleContext = "OBD: not connected")

    @Test
    fun unfilteredSwearsAndRoastsButStaysAccurate() {
        val p = prompt(PersonaStyle.Unfiltered)
        assertTrue(p.contains("Every reply contains real profanity"))
        assertTrue(p.contains("Roast the vehicle"))
        assertTrue(p.contains("driving skills"))
        assertTrue(p.contains("Accuracy is non-negotiable"))
        assertTrue(p.contains("never make up a reading"))
        assertTrue(p.contains("no slurs"))
    }

    @Test
    fun classicStaysPolite() {
        val p = prompt(PersonaStyle.Classic)
        assertFalse(p.contains("profanity"))
        assertTrue(p.contains("Accuracy is non-negotiable"))
    }
}
