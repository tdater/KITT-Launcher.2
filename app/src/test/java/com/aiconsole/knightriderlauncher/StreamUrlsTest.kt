package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.stream.StreamUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamUrlsTest {

    @Test
    fun bareDomainsGetHttps() {
        assertEquals("https://twitch.tv", StreamUrls.normalize("  twitch.tv "))
        assertEquals("https://www.pluto.tv/live", StreamUrls.normalize("www.pluto.tv/live"))
    }

    @Test
    fun fullAddressesAreKept() {
        assertEquals("http://192.168.1.5:8096", StreamUrls.normalize("http://192.168.1.5:8096"))
        assertEquals("HTTPS://Example.com", StreamUrls.normalize("HTTPS://Example.com"))
    }

    @Test
    fun searchWordsAndBlanksAreRejected() {
        assertNull(StreamUrls.normalize(""))
        assertNull(StreamUrls.normalize("cat videos"))
        assertNull(StreamUrls.normalize("youtube"))
    }

    @Test
    fun titleIsTheBareHost() {
        assertEquals("twitch.tv", StreamUrls.titleOf("https://www.twitch.tv/directory"))
        assertEquals("youtube.com", StreamUrls.titleOf("https://m.youtube.com/"))
    }
}

class StreamUrlsExtrasTest {

    @Test
    fun userscriptLinksAreRecognisedWithQueries() {
        org.junit.Assert.assertTrue(StreamUrls.isUserscript("https://update.greasyfork.org/scripts/1/Foo.user.js"))
        org.junit.Assert.assertTrue(StreamUrls.isUserscript("https://x.org/a.USER.JS?v=2#top"))
        org.junit.Assert.assertFalse(StreamUrls.isUserscript("https://x.org/app.js"))
    }

    @Test
    fun desktopUserAgentKeepsWebViewChromeVersion() {
        val mobile = "Mozilla/5.0 (Linux; Android 13; HU wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.44 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.7390.44 Safari/537.36",
            StreamUrls.desktopUserAgent(mobile)
        )
    }
}

class StreamSitesTest {
    @Test
    fun savedSitesRoundTripAndBuiltInsAreHttps() {
        val saved = listOf(
            com.aiconsole.knightriderlauncher.stream.StreamSite("My | Radio", "https://example.org/live"),
            com.aiconsole.knightriderlauncher.stream.StreamSite("Tube", "https://www.youtube.com/")
        )
        val back = com.aiconsole.knightriderlauncher.stream.StreamSites.decode(
            com.aiconsole.knightriderlauncher.stream.StreamSites.encode(saved) + "\nnot a site\n|"
        )
        assertEquals(listOf("My / Radio", "Tube"), back.map { it.label })
        assertEquals(saved.map { it.url }, back.map { it.url })
        val all = com.aiconsole.knightriderlauncher.stream.StreamSites.video + com.aiconsole.knightriderlauncher.stream.StreamSites.audio
        org.junit.Assert.assertTrue(all.all { it.url.startsWith("https://") })
        assertEquals(all.size, all.map { it.url }.distinct().size)
    }
}
