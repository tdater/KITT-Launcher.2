package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.PerplexityRouting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerplexityRoutingTest {

    private fun web(prompt: String, diagnostic: Boolean = false, enabled: Boolean = true) =
        PerplexityRouting.needsWeb(prompt, diagnostic, enabled)

    @Test
    fun vehicleDataAndChatSkipTheSearch() {
        assertFalse(web("What's my coolant temperature?"))
        assertFalse(web("How was that drive?"))
        assertFalse(web("Do I have codes right now"))
        assertFalse(web("hey KITT"))
        assertFalse(web("thanks"))
    }

    @Test
    fun currentAndWorldFactsSearch() {
        assertTrue(web("What's the weather tomorrow in Austin?"))
        assertTrue(web("Cheapest gas station near me"))
        assertTrue(web("Any recalls for a 2017 Colorado?"))
        assertTrue(web("Who won the game last night"))
    }

    @Test
    fun diagnosticsSearchEvenWhenAboutTheTruck() {
        assertTrue(web("Why is my P0300 code back", diagnostic = true))
    }

    @Test
    fun unclearDefaultsToSearchAndTheSwitchWins() {
        assertTrue(web("How does a torque converter lock up?"))
        assertFalse(web("Any recalls for my truck?", enabled = false))
    }
}
