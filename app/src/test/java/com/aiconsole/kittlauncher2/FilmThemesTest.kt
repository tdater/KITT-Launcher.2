package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.FilmCharacter
import com.aiconsole.kittlauncher2.assistant.KittPersona
import com.aiconsole.kittlauncher2.data.settings.PersonaStyle
import com.aiconsole.kittlauncher2.ui.theme.FilmHudText
import com.aiconsole.kittlauncher2.ui.theme.HudStyle
import com.aiconsole.kittlauncher2.ui.theme.TimeCircuits
import com.aiconsole.kittlauncher2.ui.theme.UiTheme
import com.aiconsole.kittlauncher2.ui.theme.hud
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmThemesTest {

    @Test
    fun everyFilmThemeHasACharacterAndText() {
        assertEquals(HudStyle.Future, UiTheme.BackToTheFuture.hud)
        HudStyle.entries.forEach { style ->
            val c = FilmCharacter.forStyle(style)!!
            if (BuildConfig.HOLO_DESIGN) {
                assertTrue(c.personality.contains("quoted freely"))
            } else {
                assertTrue(c.style.contains("Do not imitate any real actor"))
                assertTrue(c.personality.contains("Never recite"))
            }
            assertTrue(FilmHudText.intro(style).isNotEmpty())
            assertTrue(FilmHudText.lines(style).isNotEmpty())
            assertTrue(c.greeting("Good morning", ", Travis").startsWith("Good morning, Travis"))
        }
        assertNull(FilmCharacter.forStyle(null))
    }

    @Test
    fun characterLayersOnTopOfThePersona() {
        val p = KittPersona.systemInstruction(PersonaStyle.Savage, "", false, "OBD: not connected", FilmCharacter.Cyborg)
        assertTrue(p.contains("cyborg infiltration unit"))
        assertTrue(p.contains("Savage mode")) // The crude persona still applies.
        assertTrue(p.contains("Accuracy is non-negotiable"))
        assertFalse(KittPersona.systemInstruction(PersonaStyle.Classic, "", false, "x").contains("Theme character"))
    }

    @Test
    fun timeCircuitsFormatLikeTheFilm() {
        // 26 Oct 1985, 01:21: the film's famous departure (in UTC here).
        val utc = TimeZone.getTimeZone("UTC")
        val row = TimeCircuits.row(499_137_660_000L, utc) // 1985-10-26 01:21 UTC
        assertEquals("OCT", row.month)
        assertEquals("26 1985 0121", row.digits)
    }

    @Test
    fun liveTerminatorCodeReflectsTelemetryAndEdition() {
        val snap = com.aiconsole.kittlauncher2.ui.theme.LiveTelemetrySnapshot(
            rpm = 2560,
            speed = 68,
            speedUnit = "MPH",
            coolantTemp = 198,
            tempUnit = "°F",
            throttlePct = 42,
            loadPct = 55,
            volts = 14.2,
            fuelPct = 76,
            hz = 8.4,
            dtcCount = 0,
            linked = true,
            speedLimit = 65,
            overLimit = true,
            speaking = true,
            tick = 3
        )
        val regs = FilmHudText.cpuRegisters(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.T800)
        assertTrue(regs.contains("PC:$0306"))
        assertTrue(regs.contains("A:$50")) // 2560 / 32 = 80 = 0x50
        assertTrue(regs.contains("X:$44")) // 68 = 0x44

        val t800Lines = FilmHudText.liveTerminatorCode(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.T800)
        assertEquals(16, t800Lines.size)
        assertTrue(t800Lines[0].contains("CSM-101"))
        assertTrue(t800Lines.any { it.contains("LDA #$50") && it.contains("RPM=2560") })
        assertTrue(t800Lines.any { it.contains("LDX #$44") && it.contains("VEL=68MPH") })
        assertTrue(t800Lines.any { it.contains("ECT=198°F") })
        assertTrue(t800Lines.any { it.contains("OBD=8.4HZ") })

        val t1000Lines = FilmHudText.liveTerminatorCode(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.T1000)
        assertTrue(t1000Lines[0].contains("POLYALLOY"))
        assertTrue(t1000Lines.any { it.contains("MORPH_SYNC") })

        val fwLines = FilmHudText.liveTerminatorCode(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.FutureWar)
        assertTrue(fwLines[0].contains("SKYNET HK-NET"))
        assertTrue(fwLines.any { it.contains("HK_RADAR") })
    }

    @Test
    fun liveMatrixRobocopAndBttfAdaptToTelemetryAndEditions() {
        val snap = com.aiconsole.kittlauncher2.ui.theme.LiveTelemetrySnapshot(
            rpm = 3200,
            speed = 88,
            speedUnit = "MPH",
            coolantTemp = 92,
            tempUnit = "°C",
            throttlePct = 70,
            loadPct = 80,
            volts = 14.1,
            fuelPct = 60,
            hz = 9.5,
            dtcCount = 1,
            linked = true,
            tick = 2
        )
        val matrixLines = FilmHudText.liveMatrixConsole(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.RealWorld)
        assertTrue(matrixLines[0].contains("MJORLNIR"))
        assertTrue(matrixLines.any { it.contains("RPM=3200") && it.contains("VEL=88 MPH") })
        assertTrue(matrixLines.any { it.contains("hover.pads") })
        val glyphs = FilmHudText.liveMatrixGlyphStream(snap)
        assertTrue(glyphs.contains("3200RPM"))
        assertTrue(glyphs.contains("088MPH"))

        val ed209Dirs = FilmHudText.liveRobocopDirectives(com.aiconsole.kittlauncher2.ui.theme.FilmEdition.Ed209)
        assertEquals(4, ed209Dirs.size)
        assertTrue(ed209Dirs.any { it.second.contains("20-SECOND COMPLIANCE") })
        val copVitals = FilmHudText.liveRobocopTelemetry(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.Ed209)
        assertTrue(copVitals.any { it.second.contains("88 MPH") })
        assertTrue(copVitals.any { it.second.contains("1 FAULT(S)") })

        val bttf1985 = FilmHudText.liveBttfCircuits(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.Delorean1985)
        assertEquals("OCT 26 1985 01:21", bttf1985.destinationFull)
        assertEquals(1.0f, bttf1985.fluxFraction, 0.001f)
        assertTrue(bttf1985.fluxStatus.contains("1.21 GW // TIME DISPLACEMENT"))

        val bttf1885 = FilmHudText.liveBttfCircuits(snap, com.aiconsole.kittlauncher2.ui.theme.FilmEdition.Frontier1885)
        assertEquals("SEP 02 1885 08:00", bttf1885.destinationFull)
        assertTrue(bttf1885.coreStatus.contains("YELLOW LOG"))
    }
}
