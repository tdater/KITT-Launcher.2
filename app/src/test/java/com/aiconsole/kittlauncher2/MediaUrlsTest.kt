package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.browser.MediaUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlsTest {

    @Test
    fun directVideoFilesAndStreamsAreRecognised() {
        assertTrue(MediaUrls.isDirectVideo("https://cdn.example.com/films/movie.mp4"))
        assertTrue(MediaUrls.isDirectVideo("http://example.com/a/b/stream.M3U8"))
        assertTrue(MediaUrls.isDirectVideo("https://example.com/clip.webm?token=abc&x=1"))
        assertTrue(MediaUrls.isDirectVideo("https://example.com/manifest.mpd#t=10"))
    }

    @Test
    fun pagesAndOtherSchemesAreLeftToTheBrowser() {
        assertFalse(MediaUrls.isDirectVideo("https://www.youtube.com/watch?v=abc.mp4"))
        assertFalse(MediaUrls.isDirectVideo("https://example.com/video"))
        assertFalse(MediaUrls.isDirectVideo("https://example.com/page.html"))
        assertFalse(MediaUrls.isDirectVideo("https://example.com/folder.mp4/index.html"))
        assertFalse(MediaUrls.isDirectVideo("file:///sdcard/movie.mp4"))
        assertFalse(MediaUrls.isDirectVideo("intent://x/movie.mp4"))
        assertFalse(MediaUrls.isDirectVideo(null))
    }

    @Test
    fun clockFormatsMinutesAndHours() {
        assertEquals("0:00", MediaUrls.clock(0))
        assertEquals("1:05", MediaUrls.clock(65_000))
        assertEquals("1:01:01", MediaUrls.clock(3_661_000))
        assertEquals("0:00", MediaUrls.clock(-5))
    }
}
