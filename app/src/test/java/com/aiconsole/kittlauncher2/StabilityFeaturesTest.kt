package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.LocalVoices
import com.aiconsole.kittlauncher2.data.SafeModeRules
import com.aiconsole.kittlauncher2.data.Versions
import com.aiconsole.kittlauncher2.data.trip.LastDrive
import com.aiconsole.kittlauncher2.data.trip.LastDriveRules
import com.aiconsole.kittlauncher2.obd.LinkOrder
import com.aiconsole.kittlauncher2.ui.settings.SettingsSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityFeaturesTest {

    @Test
    fun safeModeTripsOnThreeRecentStartupCrashes() {
        val now = 10_000_000L
        assertFalse(SafeModeRules.shouldTrip(listOf(now - 1_000, now - 2_000), now))
        assertTrue(SafeModeRules.shouldTrip(listOf(now - 1_000, now - 2_000, now - 3_000), now))
        // Old crashes don't count.
        assertFalse(SafeModeRules.shouldTrip(listOf(now - SafeModeRules.SPAN_MS - 1, now - 2_000, now - 3_000), now))
        val crashes = listOf(1L, 2L, 3L)
        assertEquals(crashes, SafeModeRules.decode(SafeModeRules.encode(crashes)))
        assertEquals(emptyList<Long>(), SafeModeRules.decode(null))
    }

    @Test
    fun betaVersionsSortBeforeTheirRelease() {
        assertTrue(Versions.isNewer("v1.8", "1.8-beta2"))
        assertTrue(Versions.isNewer("1.8-beta2", "1.8-beta1"))
        assertTrue(Versions.isNewer("1.8-beta1", "1.7"))
        assertFalse(Versions.isNewer("1.8-beta1", "1.8"))
        assertFalse(Versions.isNewer("1.8", "1.8"))
        assertEquals(listOf(1, 8), Versions.parts("v1.8-beta2"))
        assertEquals("v1.7", Versions.pick(listOf("v1.6", "v1.8-beta1", "v1.7"), beta = false))
        assertEquals("v1.8-beta1", Versions.pick(listOf("v1.6", "v1.8-beta1", "v1.7"), beta = true))
        assertEquals("v1.8", Versions.pick(listOf("v1.8-beta3", "v1.8"), beta = true))
    }

    @Test
    fun rememberedLinkMethodGoesFirst() {
        val attempts = listOf("secure SPP", "insecure SPP", "RFCOMM channel 1")
        assertEquals(listOf("RFCOMM channel 1", "secure SPP", "insecure SPP"), LinkOrder.preferred(attempts, "RFCOMM channel 1") { it })
        assertEquals(attempts, LinkOrder.preferred(attempts, null) { it })
        assertEquals(attempts, LinkOrder.preferred(attempts, "gone") { it })
    }

    @Test
    fun lastDriveRecapSaysWhatMatters() {
        val ended = 1_000_000_000L
        val later = ended + LastDriveRules.MIN_GAP_MS + 1
        val drive = LastDrive(ended, "B", 20.0, listOf("P0420"), listOf("Battery looked weak at start."))
        val recap = LastDriveRules.recap(drive, later, metric = false)!!
        assertTrue("P0420" in recap && "battery looked weak" in recap && "B over 12 miles" in recap)
        assertEquals(drive, LastDriveRules.decode(LastDriveRules.encode(drive)))
        // A dropout mid-drive and stale news say nothing; neither does a quiet drive with no grade.
        assertNull(LastDriveRules.recap(drive, ended + 60_000, metric = false))
        assertNull(LastDriveRules.recap(drive, ended + LastDriveRules.MAX_AGE_MS + 1, metric = false))
        assertNull(LastDriveRules.recap(LastDrive(ended), later, metric = true))
    }

    @Test
    fun settingsSearchFindsSections() {
        assertEquals("updates", SettingsSearch.match("beta").first().anchor)
        assertEquals("voice", SettingsSearch.match("Kokoro").first().anchor)
        assertTrue(SettingsSearch.match("shift light").any { it.anchor == "drivinglayout" })
        assertTrue(SettingsSearch.match("   ").isEmpty())
        assertTrue(SettingsSearch.match("zzzz").isEmpty())
    }

    @Test
    fun everyVoiceHasAChecksum() {
        assertTrue(LocalVoices.ALL.all { it.sha256.length == 64 && it.bytes > 1_000_000 })
    }
}
