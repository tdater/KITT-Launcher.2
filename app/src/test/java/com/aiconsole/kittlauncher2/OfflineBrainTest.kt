package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.OfflinePrompt
import com.aiconsole.kittlauncher2.data.settings.PersonaStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineBrainTest {

    @Test
    fun systemPromptIsShortAndCarriesTheData() {
        val data = "LIVE DATA:\n- Coolant temperature: 118 C\n" + "x".repeat(5_000)
        val prompt = OfflinePrompt.system(PersonaStyle.Classic, "Travis", data)
        assertTrue("Coolant temperature: 118 C" in prompt)
        assertTrue("Travis" in prompt && "offline" in prompt && "stop somewhere safe" in prompt)
        assertTrue(prompt.length < OfflinePrompt.MAX_CONTEXT_CHARS + 900)
        assertFalse("Travis" in OfflinePrompt.system(PersonaStyle.Karr, " ", data))
    }

    @Test
    fun repliesLoseReasoningAndMarkdown() {
        assertEquals("Coolant is fine.", OfflinePrompt.clean("<think>hmm</think>\n**Coolant** is   fine."))
        assertEquals("Stop now.", OfflinePrompt.clean("<think></think>Stop now."))
    }
}
