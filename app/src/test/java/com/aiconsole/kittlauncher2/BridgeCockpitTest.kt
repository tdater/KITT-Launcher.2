package com.aiconsole.kittlauncher2

import androidx.compose.ui.graphics.Color
import com.aiconsole.kittlauncher2.ui.theme.BridgePalette
import com.aiconsole.kittlauncher2.ui.theme.KittClassicPalette
import com.aiconsole.kittlauncher2.ui.theme.Starfield
import com.aiconsole.kittlauncher2.ui.theme.ThemeState
import com.aiconsole.kittlauncher2.ui.theme.UiTheme
import com.aiconsole.kittlauncher2.ui.theme.hud
import com.aiconsole.kittlauncher2.ui.theme.lightStrip
import com.aiconsole.kittlauncher2.ui.theme.palette
import com.aiconsole.kittlauncher2.ui.theme.withAccent
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BridgeCockpitTest {

    @After
    fun reset() {
        ThemeState.theme = UiTheme.Kitt
        ThemeState.bridgeCockpit = false
        ThemeState.accentOverride = null
    }

    @Test
    fun bridgeThemeIsAColourThemeWithItsOwnPalette() {
        assertSame(BridgePalette, UiTheme.Bridge.palette())
        assertNull(UiTheme.Bridge.hud)
        // Stored by name, and the existing themes keep theirs.
        assertEquals(UiTheme.Bridge, UiTheme.valueOf("Bridge"))
        assertEquals(UiTheme.Colorado, UiTheme.valueOf("Colorado"))
    }

    @Test
    fun bridgeKeepsKittsRedScannerWithCyanLightStrips() {
        assertEquals(Color(0xFFFF3B30), BridgePalette.accent)
        assertEquals(Color(0xFF3FE0EF), BridgePalette.lightStrip)
    }

    @Test
    fun everyOtherThemeLightsItsBridgeInItsOwnAccent() {
        UiTheme.entries.filter { it != UiTheme.Bridge }.forEach { theme ->
            assertEquals(theme.name, theme.palette().accent, theme.palette().lightStrip)
        }
    }

    @Test
    fun aCustomAccentAlsoColoursTheLightStrips() {
        val pink = Color(0xFFFF2BD6)
        assertEquals(pink, BridgePalette.withAccent(pink).lightStrip)
        assertEquals(pink, KittClassicPalette.withAccent(pink).lightStrip)
        assertEquals(BridgePalette.lightStrip, BridgePalette.withAccent(null).lightStrip)
    }

    @Test
    fun cockpitIsOnWithTheSettingOrTheBridgeTheme() {
        ThemeState.theme = UiTheme.Kitt
        ThemeState.bridgeCockpit = false
        assertFalse(ThemeState.bridge)
        ThemeState.bridgeCockpit = true
        assertTrue(ThemeState.bridge)
        ThemeState.bridgeCockpit = false
        ThemeState.theme = UiTheme.Bridge
        assertTrue(ThemeState.bridge)
        // Works over the film themes too.
        ThemeState.theme = UiTheme.Terminator
        ThemeState.bridgeCockpit = true
        assertTrue(ThemeState.bridge)
    }

    @Test
    fun starsStandStillWhenParked() {
        val field = Starfield(50, Random(7))
        val before = field.radius.copyOf()
        field.step(500f, 0f)
        field.step(500f, Starfield.speedFraction(0.0))
        field.step(500f, Starfield.speedFraction(null))
        assertArrayEquals(before, field.radius, 0f)
    }

    @Test
    fun starsDriftOutwardWhileMovingAndAreRebornInTheCentre() {
        val field = Starfield(200, Random(3))
        val before = field.radius.copyOf()
        field.step(33f, 0.5f)
        for (i in 0 until field.size) {
            // Either moved out, or left the window and came back near the centre.
            assertTrue(field.radius[i] > before[i] || field.radius[i] <= Starfield.NEW_STAR_RADIUS)
        }
        repeat(2_000) { field.step(33f, 1f) }
        for (i in 0 until field.size) {
            assertTrue(field.radius[i] in 0f..1f)
            assertTrue(field.depth[i] in 0.3f..1f)
        }
    }

    @Test
    fun fasterDrivingDriftsFaster() {
        val slow = Starfield(100, Random(11)).also { it.step(33f, 0.2f) }
        val fast = Starfield(100, Random(11)).also { it.step(33f, 0.9f) }
        val start = Starfield(100, Random(11))
        var slowGain = 0f
        var fastGain = 0f
        for (i in 0 until start.size) {
            if (slow.radius[i] > start.radius[i] && fast.radius[i] > start.radius[i]) {
                slowGain += slow.radius[i] - start.radius[i]
                fastGain += fast.radius[i] - start.radius[i]
            }
        }
        assertTrue(fastGain > slowGain * 3f)
    }

    @Test
    fun aLongPauseDoesNotFlingTheStars() {
        val a = Starfield(100, Random(5)).also { it.step(Starfield.MAX_STEP_MS, 1f) }
        val b = Starfield(100, Random(5)).also { it.step(60_000f, 1f) }
        assertArrayEquals(a.radius, b.radius, 0f)
    }

    @Test
    fun speedFractionScalesToHighwaySpeed() {
        assertEquals(0f, Starfield.speedFraction(-5.0), 0f)
        assertEquals(0f, Starfield.speedFraction(Double.NaN), 0f)
        assertEquals(0.5f, Starfield.speedFraction(Starfield.FULL_SPEED_KMH / 2), 0.0001f)
        assertEquals(1f, Starfield.speedFraction(200.0), 0f)
    }
}
