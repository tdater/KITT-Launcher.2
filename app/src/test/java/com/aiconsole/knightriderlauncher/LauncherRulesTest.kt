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

class ProfileRulesTest {
    @Test
    fun truckSettingsAreSharedAndDriverSettingsPersonal() {
        listOf("obd_adapter_type", "vehicle_identity", "odometer_base_km", "distance_counter_km", "maintenance_items", "fuel_log", "tank_size", "widget_ids")
            .forEach { assertFalse(it, com.aiconsole.knightriderlauncher.data.ProfileRules.isPersonal(it)) }
        listOf("ui_theme", "persona", "gemini_voice", "pinned_apps", "stream_bookmarks", "accent_color")
            .forEach { assertTrue(it, com.aiconsole.knightriderlauncher.data.ProfileRules.isPersonal(it)) }
    }

    @Test
    fun profileNamesAreFileSafe() {
        assertEquals("Travis 2", com.aiconsole.knightriderlauncher.data.ProfileRules.sanitize("  Travis/../ 2 "))
        assertEquals(null, com.aiconsole.knightriderlauncher.data.ProfileRules.sanitize("../"))
    }
}

class VersionsTest {
    private val v = com.aiconsole.knightriderlauncher.data.Versions

    @Test
    fun comparesNumericSegments() {
        assertTrue(v.isNewer("v1.0.42", "1.0"))
        assertTrue(v.isNewer("1.0.10", "1.0.9"))
        assertFalse(v.isNewer("v1.0.9", "1.0.10"))
        assertFalse(v.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun repoFormat() {
        assertTrue(v.isValidRepo("travisdater/KnightRiderLauncher"))
        assertFalse(v.isValidRepo("https://github.com/x/y"))
        assertFalse(v.isValidRepo("justname"))
    }
}

class AppFilterTest {
    @Test
    fun parsesComponentsIncludingShortClassNames() {
        val xml = """
            <resources>
              <item component="ComponentInfo{com.waze/com.waze.FreeMapAppActivity}" drawable="waze" />
              <item drawable="ignored" />
              <item component="ComponentInfo{com.spotify.music/.MainActivity}" drawable="spotify"/>
              <item component=":CALENDAR" drawable="calendar" />
            </resources>
        """.trimIndent()
        val map = com.aiconsole.knightriderlauncher.data.AppFilter.parse(xml)
        assertEquals(
            mapOf("com.waze/com.waze.FreeMapAppActivity" to "waze", "com.spotify.music/com.spotify.music.MainActivity" to "spotify"),
            map
        )
    }
}

class FilmHudTextTest {
    private val t = com.aiconsole.knightriderlauncher.ui.theme.FilmHudText

    @Test
    fun hexTagIsStableAndCaseInsensitive() {
        assertEquals(t.hexTag("Navigation"), t.hexTag("NAVIGATION"))
        assertTrue(t.hexTag("Media").matches(Regex("0x[0-9A-F]{4}")))
    }

    @Test
    fun robocopKeepsTheFourthDirectiveClassified() {
        val lines = t.lines(com.aiconsole.knightriderlauncher.ui.theme.HudStyle.RoboCop)
        assertEquals(4, lines.size)
        assertTrue(lines.last().contains("CLASSIFIED"))
    }
}
