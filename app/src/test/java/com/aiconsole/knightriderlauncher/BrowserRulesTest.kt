package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.browser.AdBlock
import com.aiconsole.knightriderlauncher.browser.AiSites
import com.aiconsole.knightriderlauncher.browser.Bookmarks
import com.aiconsole.knightriderlauncher.browser.Gamepad
import com.aiconsole.knightriderlauncher.browser.PageScripts
import com.aiconsole.knightriderlauncher.browser.WebViewVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRulesTest {

    @Test
    fun adBlockMatchesDomainsAndSubdomainsOnly() {
        assertTrue(AdBlock.isBlocked("doubleclick.net"))
        assertTrue(AdBlock.isBlocked("stats.g.doubleclick.net"))
        assertTrue(AdBlock.isBlocked("pagead2.googlesyndication.com"))
        assertFalse(AdBlock.isBlocked("notdoubleclick.net"))
        assertFalse(AdBlock.isBlocked("www.youtube.com"))
        assertFalse(AdBlock.isBlocked(null))
    }

    @Test
    fun bookmarksParseNetscapeExportAndRoundTrip() {
        val html = """
            <DL><p>
            <DT><H3>Car</H3>
            <DL><p>
              <DT><A HREF="https://tubitv.com/" ADD_DATE="1">Tubi &amp; more</A>
              <DT><A HREF="javascript:alert(1)">Bad</A>
              <DT><A HREF="https://somafm.com/">SomaFM</A>
              <DT><A HREF="https://tubitv.com/">Dup</A>
            </DL><p>
        """.trimIndent()
        val parsed = Bookmarks.parse(html)
        assertEquals(listOf("Tubi & more" to "https://tubitv.com/", "SomaFM" to "https://somafm.com/"), parsed)
        assertEquals(parsed, Bookmarks.parse(Bookmarks.export(parsed)))
    }

    @Test
    fun webViewVersionCheck() {
        assertEquals(91, WebViewVersion.major("91.0.4472.114"))
        assertTrue(WebViewVersion.isOutdated("91.0.4472.114"))
        assertFalse(WebViewVersion.isOutdated("141.0.7390.44"))
        assertFalse(WebViewVersion.isOutdated(null))
    }

    @Test
    fun aiSitesAndPromptEscaping() {
        assertEquals("Claude", AiSites.forHost("claude.ai")?.label)
        assertEquals("ChatGPT", AiSites.forHost("chatgpt.com")?.label)
        assertNull(AiSites.forHost("example.com"))
        val js = AiSites.sendPrompt("He said \"stop\"\nnow")
        assertTrue(js.contains("var text = \"He said \\\"stop\\\"\\nnow\";"))
    }

    @Test
    fun gamepadMappingAndStick() {
        assertEquals(Gamepad.DPAD_UP, Gamepad.map(Gamepad.DPAD_UP))
        assertEquals(66, Gamepad.map(Gamepad.BUTTON_START))
        assertNull(Gamepad.map(4))
        assertEquals(setOf(Gamepad.DPAD_LEFT, Gamepad.DPAD_UP), Gamepad.stickDirections(-0.9f, -0.6f))
        assertEquals(emptySet<Int>(), Gamepad.stickDirections(0.2f, -0.3f))
    }

    @Test
    fun jsStringEscapesLineSeparators() {
        assertEquals("\"a\\u2028b\"", PageScripts.jsString("a\u2028b"))
    }
}

class SiteSettingsTest {
    private val s = com.aiconsole.knightriderlauncher.browser.SiteSettings

    @Test
    fun roundTripAndUpdate() {
        var sites = s.update(emptyMap(), "www.Example.com") { it.copy(desktop = false, textZoom = 125) }
        sites = s.update(sites, "m.music.youtube.com") { it.copy(dark = true) }
        val back = s.decode(s.encode(sites))
        assertEquals(sites, back)
        assertEquals(false, back["example.com"]?.desktop)
        assertEquals(125, back["example.com"]?.textZoom)
        assertEquals(true, back["music.youtube.com"]?.dark)
        assertNull(back["music.youtube.com"]?.blockAds)
    }

    @Test
    fun youtubeIsNeverAdBlocked() {
        assertFalse(s.blockAllowed("www.youtube.com"))
        assertFalse(s.blockAllowed("music.youtube.com"))
        assertTrue(s.blockAllowed("tubitv.com"))
    }
}
