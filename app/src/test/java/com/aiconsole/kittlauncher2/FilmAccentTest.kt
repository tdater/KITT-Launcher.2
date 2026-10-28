package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.FilmCharacter
import com.aiconsole.kittlauncher2.assistant.LocalVoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmAccentTest {

    @Test
    fun cyborgSpeaksWithAnAustrianRespelling() {
        assertEquals("Ze mission vill start. Zat is bahk.", FilmCharacter.Cyborg.accentText("The mission will start. That is back."))
    }

    @Test
    fun everyoneElseIsSpokenAsWritten() {
        FilmCharacter.entries.filter { it != FilmCharacter.Cyborg && it != FilmCharacter.Enforcer }.forEach { c ->
            assertEquals(c.name, "The mission will start.", c.accentText("The mission will start."))
        }
    }

    @Test
    fun enforcerSpeaksInStaccato() {
        assertEquals("You. have. twenty. seconds. to. comply.", FilmCharacter.Enforcer.accentText("You have twenty seconds to comply."))
        // Longer lines get a pause every third word instead.
        assertEquals(
            "Coolant is running, hot and rising, fast please slow, down now",
            FilmCharacter.Enforcer.accentText("Coolant is running hot and rising fast please slow down now")
        )
    }

    @Test
    fun everyCharacterHintsAVoiceThatExists() {
        FilmCharacter.entries.forEach { c ->
            assertTrue(c.name, LocalVoices.ALL.any { it.id == c.localVoiceHint })
        }
    }
}
