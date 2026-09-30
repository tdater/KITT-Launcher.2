package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.ui.panes.StreamUrls
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
