package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.data.AppGroup
import com.aiconsole.knightriderlauncher.data.AppUsage
import com.aiconsole.knightriderlauncher.data.Compass
import com.aiconsole.knightriderlauncher.data.KeyAction
import com.aiconsole.knightriderlauncher.data.KeyBindings
import com.aiconsole.knightriderlauncher.data.StartupRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherRulesTest {

    @Test
    fun groupsPreferCategoryThenKeywords() {
        assertEquals(AppGroup.Navigation, AppGroup.of(6, "Some App", "com.x"))
        assertEquals(AppGroup.Music, AppGroup.of(1, "Spotify", "com.spotify.music"))
        assertEquals(AppGroup.Car, AppGroup.of(null, "Car Scanner", "com.ovz.carscanner"))
        assertEquals(AppGroup.Car, AppGroup.of(7, "Torque Pro", "org.prowl.torque"))
        assertEquals(AppGroup.Navigation, AppGroup.of(null, "Waze", "com.waze"))
        assertEquals(AppGroup.Video, AppGroup.of(null, "YouTube", "com.google.android.youtube"))
        assertEquals(AppGroup.Music, AppGroup.of(null, "YouTube Music", "com.google.android.apps.youtube.music"))
        assertEquals(AppGroup.Other, AppGroup.of(null, "Solitaire", "com.cards"))
    }

    @Test
    fun usageCountsRankAndRoundTrip() {
        var counts = emptyMap<String, Int>()
        repeat(3) { counts = AppUsage.record(counts, "a/b") }
        counts = AppUsage.record(counts, "c/d")
        assertEquals(listOf("a/b", "c/d"), AppUsage.mostUsed(counts, 5))
        assertEquals(counts, AppUsage.decode(AppUsage.encode(counts)))
    }

    @Test
    fun compassPoints() {
        assertEquals("N", Compass.cardinal(0f))
        assertEquals("N", Compass.cardinal(350f))
        assertEquals("NE", Compass.cardinal(40f))
        assertEquals("S", Compass.cardinal(180f))
        assertEquals("W", Compass.cardinal(-90f))
    }

    @Test
    fun keyBindingsRoundTrip() {
        val map = mapOf(231 to KeyAction.OpenKitt, 87 to KeyAction.NextPage)
        assertEquals(map, KeyBindings.decode(KeyBindings.encode(map) + "\nbad\n5=Nope"))
    }

    @Test
    fun startupRunsOnFirstStartOrLongSleep() {
        assertTrue(StartupRule.shouldRun(true, null))
        assertTrue(StartupRule.shouldRun(false, 6 * 60_000L))
        assertFalse(StartupRule.shouldRun(false, 60_000L))
        assertFalse(StartupRule.shouldRun(false, null))
    }
}
