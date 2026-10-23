package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.AiAppHandoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiAppHandoffTest {
    @Test
    fun siteLabelFindsItsOfficialApp() {
        assertEquals("com.anthropic.claude", AiAppHandoff.appFor("Claude")?.packageName)
        assertEquals("Gemini", AiAppHandoff.appFor("gemini")?.label)
    }

    @Test
    fun sitesWithoutAnAppOrAnInstallGetNothing() {
        assertNull(AiAppHandoff.appFor("ChatGPT"))
        assertNull(AiAppHandoff.appFor(null))
        assertNull(AiAppHandoff.appFor("Claude", emptyList()))
    }
}
