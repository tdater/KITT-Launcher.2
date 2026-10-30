package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.stream.StreamUrls
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
        assertNull(StreamUrls.normalizeOrSearch("   "))
        assertEquals("https://tubitv.com", StreamUrls.normalizeOrSearch("tubitv.com"))
        assertEquals("https://duckduckgo.com/?q=knight+rider+full+episodes", StreamUrls.normalizeOrSearch("knight rider full episodes"))
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
            com.aiconsole.kittlauncher2.stream.StreamSite("My | Radio", "https://example.org/live"),
            com.aiconsole.kittlauncher2.stream.StreamSite("Tube", "https://www.youtube.com/")
        )
        val back = com.aiconsole.kittlauncher2.stream.StreamSites.decode(
            com.aiconsole.kittlauncher2.stream.StreamSites.encode(saved) + "\nnot a site\n|"
        )
        assertEquals(listOf("My / Radio", "Tube"), back.map { it.label })
        assertEquals(saved.map { it.url }, back.map { it.url })
        val all = com.aiconsole.kittlauncher2.stream.StreamSites.video + com.aiconsole.kittlauncher2.stream.StreamSites.audio
        org.junit.Assert.assertTrue(all.all { it.url.startsWith("https://") })
        assertEquals(all.size, all.map { it.url }.distinct().size)
        val sections = com.aiconsole.kittlauncher2.stream.StreamSites.FEATURED_SECTIONS
        org.junit.Assert.assertEquals(9, sections.size)
        org.junit.Assert.assertEquals("Live Sports Streams", sections[0].first)
        org.junit.Assert.assertEquals("Live TV Channels", sections[1].first)
        org.junit.Assert.assertEquals("Free Movies & TV Portals", sections[2].first)
        org.junit.Assert.assertEquals("https://tubitv.com/", com.aiconsole.kittlauncher2.stream.StreamSites.FREE_LICENSED_STREAMING.first().url)
        org.junit.Assert.assertEquals("https://open.spotify.com/", com.aiconsole.kittlauncher2.stream.StreamSites.PICK_A_SONG.first().url)
        org.junit.Assert.assertEquals("https://www.pandora.com/", com.aiconsole.kittlauncher2.stream.StreamSites.FREE_RADIO_AND_STATIONS.first().url)
        org.junit.Assert.assertEquals("https://bandcamp.com/", com.aiconsole.kittlauncher2.stream.StreamSites.INDEPENDENT_MUSIC_AND_MIXES.first().url)
    }
}
