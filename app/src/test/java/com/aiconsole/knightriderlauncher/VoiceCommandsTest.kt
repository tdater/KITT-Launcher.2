package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.assistant.VoiceCommand
import com.aiconsole.knightriderlauncher.assistant.VoiceCommands
import com.aiconsole.knightriderlauncher.assistant.WakeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {

    @Test
    fun appAndPageCommands() {
        assertEquals(VoiceCommand.OpenApp("waze"), VoiceCommands.parse("Open Waze."))
        assertEquals(VoiceCommand.OpenApp("google maps"), VoiceCommands.parse("KITT, launch the Google Maps app please"))
        assertEquals(VoiceCommand.ShowPage("gauges"), VoiceCommands.parse("show gauges"))
        assertEquals(VoiceCommand.ShowPage("drive"), VoiceCommands.parse("go to the map screen"))
        assertEquals(VoiceCommand.OpenDiagnostics, VoiceCommands.parse("open diagnostics"))
    }

    @Test
    fun mediaCommands() {
        assertEquals(VoiceCommand.Play, VoiceCommands.parse("play music"))
        assertEquals(VoiceCommand.Play, VoiceCommands.parse("resume"))
        assertEquals(VoiceCommand.Pause, VoiceCommands.parse("pause the music"))
        assertEquals(VoiceCommand.NextTrack, VoiceCommands.parse("skip song"))
        assertEquals(VoiceCommand.PreviousTrack, VoiceCommands.parse("previous track"))
    }

    @Test
    fun vehicleAndStreamCommands() {
        assertEquals(VoiceCommand.ReadCodes, VoiceCommands.parse("read my codes"))
        assertEquals(VoiceCommand.ReadCodes, VoiceCommands.parse("Check the check engine light"))
        assertEquals(VoiceCommand.StartLogging, VoiceCommands.parse("start logging"))
        assertEquals(VoiceCommand.StopLogging, VoiceCommands.parse("stop recording"))
        assertEquals(VoiceCommand.Stream("tubi"), VoiceCommands.parse("stream tubi"))
        assertEquals(VoiceCommand.Stream("youtube music"), VoiceCommands.parse("open YouTube Music in the window"))
        assertEquals(VoiceCommand.CloseStream, VoiceCommands.parse("close the video"))
    }

    @Test
    fun questionsGoToTheAi() {
        assertNull(VoiceCommands.parse("why is my coolant temperature high"))
        assertNull(VoiceCommands.parse("what does P0301 mean"))
        assertNull(VoiceCommands.parse(""))
    }

    @Test
    fun appMatchingPrefersExactThenPrefixThenWords() {
        val labels = listOf("Maps", "Google Maps Go", "Waze", "YouTube Music", "YouTube")
        assertEquals(0, VoiceCommands.bestMatch("maps", labels))
        assertEquals(4, VoiceCommands.bestMatch("youtube", labels))
        assertEquals(3, VoiceCommands.bestMatch("music youtube", labels))
        assertEquals(2, VoiceCommands.bestMatch("waze", labels))
        assertNull(VoiceCommands.bestMatch("spotify", labels))
    }

    @Test
    fun wakeWordVariants() {
        assertTrue(WakeWord.matches("Hey KITT what's my speed"))
        assertTrue(WakeWord.matches("hey kid"))
        assertTrue(WakeWord.matches("okay kit"))
        assertFalse(WakeWord.matches("the kit is in the trunk"))
        assertFalse(WakeWord.matches("hey there"))
    }
}
