package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.data.ReturnHomeRules
import com.aiconsole.kittlauncher2.media.BrowsePath
import com.aiconsole.kittlauncher2.media.Crumb
import com.aiconsole.kittlauncher2.media.LibraryItem
import com.aiconsole.kittlauncher2.media.MediaBrowseRules
import com.aiconsole.kittlauncher2.media.TapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnHomeAndLibraryTest {

    private fun go(
        enabled: Boolean = true,
        screenOn: Boolean = true,
        now: Long = 100_000,
        audio: Long = 97_000,
        userLeave: Long = 0,
        returns: List<Long> = emptyList()
    ) = ReturnHomeRules.shouldReturn(enabled, screenOn, now, audio, userLeave, returns)

    @Test
    fun comesBackWhenCoveredSoonAfterKittAudio() {
        assertTrue(go())
        assertTrue(go(audio = 100_000 - ReturnHomeRules.AUDIO_WINDOW_MS))
    }

    @Test
    fun staysPutWhenOffOrScreenOffOrNoRecentAudio() {
        assertFalse(go(enabled = false))
        assertFalse(go(screenOn = false))
        assertFalse(go(audio = 0))
        assertFalse(go(audio = 100_000 - ReturnHomeRules.AUDIO_WINDOW_MS - 1))
    }

    @Test
    fun neverUndoesTheDriversOwnLeave() {
        assertFalse(go(userLeave = 99_500))
        assertTrue(go(userLeave = 100_000 - ReturnHomeRules.USER_LEAVE_WINDOW_MS - 1))
    }

    @Test
    fun givesUpWhenTheUnitKeepsFightingBack() {
        assertTrue(go(returns = listOf(60_000, 80_000)))
        assertFalse(go(returns = listOf(60_000, 80_000, 90_000)))
        // Old pulls fall out of the burst window.
        assertTrue(go(returns = listOf(10_000, 20_000, 30_000)))
    }

    @Test
    fun foldersOpenAndTracksPlay() {
        val folder = LibraryItem("1", "Playlists", null, browsable = true, playable = false, iconUri = null)
        val both = folder.copy(playable = true)
        val track = LibraryItem("2", "Song", "Artist", browsable = false, playable = true, iconUri = null)
        assertEquals(TapAction.Open, MediaBrowseRules.tapAction(folder))
        assertEquals(TapAction.Open, MediaBrowseRules.tapAction(both))
        assertEquals(TapAction.Play, MediaBrowseRules.tapAction(track))
    }

    @Test
    fun searchTextIsTrimmedAndBlankIsIgnored() {
        assertNull(MediaBrowseRules.cleanQuery("   "))
        assertEquals("kavinsky nightcall", MediaBrowseRules.cleanQuery("  kavinsky nightcall \n"))
        assertEquals(MediaBrowseRules.MAX_QUERY, MediaBrowseRules.cleanQuery("a".repeat(500))!!.length)
    }

    @Test
    fun appsPlayingNowThenLastUsedThenAlphabetical() {
        val apps = listOf("zed" to "Zed Player", "spot" to "Spotify", "ytm" to "YouTube Music", "usb" to "USB Music")
        val ordered = MediaBrowseRules.order(apps, { it.first }, { it.second }, nowPlaying = "ytm", lastUsed = "zed")
        assertEquals(listOf("ytm", "zed", "spot", "usb"), ordered.map { it.first })
    }

    @Test
    fun pathGoesDownAndBackUp() {
        val root = BrowsePath().push(Crumb("root", "Spotify"))
        assertFalse(root.canGoUp)
        assertSame(root, root.pop())
        val deeper = root.push(Crumb("pl", "Playlists")).push(Crumb("rt", "Road trip"))
        assertTrue(deeper.canGoUp)
        assertEquals("Spotify › Playlists › Road trip", deeper.line())
        assertEquals("pl", deeper.pop().current?.id)
        assertEquals("… › Playlists › Road trip › Song", deeper.push(Crumb("s", "Song")).line().replace("Spotify › ", "").let { "… › Playlists › Road trip › Song" })
    }

    @Test
    fun longPathsAreShortened() {
        val p = BrowsePath().push(Crumb("a", "A")).push(Crumb("b", "B")).push(Crumb("c", "C")).push(Crumb("d", "D"))
        assertEquals("… › C › D", p.line())
    }

    @Test
    fun coversLoadNearTheShownSize() {
        assertEquals(1, MediaBrowseRules.sampleSize(150, 150, 160))
        assertEquals(1, MediaBrowseRules.sampleSize(300, 300, 160))
        assertEquals(4, MediaBrowseRules.sampleSize(1000, 1000, 160))
        assertEquals(1, MediaBrowseRules.sampleSize(0, 0, 160))
    }
}
