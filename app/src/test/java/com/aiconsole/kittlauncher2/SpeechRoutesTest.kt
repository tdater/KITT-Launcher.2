package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.SpeechPath
import com.aiconsole.kittlauncher2.assistant.SpeechRoutes
import com.aiconsole.kittlauncher2.data.settings.SpeechInput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRoutesTest {

    @Test
    fun autoPrefersGeminiThenDeviceThenOffline() {
        assertEquals(SpeechPath.Gemini, SpeechRoutes.choose(SpeechInput.Auto, geminiKey = true, deviceUsable = true, offlineReady = true))
        assertEquals(SpeechPath.Device, SpeechRoutes.choose(SpeechInput.Auto, geminiKey = false, deviceUsable = true, offlineReady = true))
        assertEquals(SpeechPath.Offline, SpeechRoutes.choose(SpeechInput.Auto, geminiKey = false, deviceUsable = false, offlineReady = true))
        assertEquals(SpeechPath.None, SpeechRoutes.choose(SpeechInput.Auto, geminiKey = false, deviceUsable = false, offlineReady = false))
    }

    @Test
    fun forcedChoicesNeverSwitchEngines() {
        assertEquals(SpeechPath.Offline, SpeechRoutes.choose(SpeechInput.Offline, geminiKey = true, deviceUsable = true, offlineReady = true))
        assertEquals(SpeechPath.None, SpeechRoutes.choose(SpeechInput.Offline, geminiKey = true, deviceUsable = true, offlineReady = false))
        assertEquals(SpeechPath.None, SpeechRoutes.choose(SpeechInput.Device, geminiKey = true, deviceUsable = false, offlineReady = true))
        assertEquals(SpeechPath.Gemini, SpeechRoutes.choose(SpeechInput.Gemini, geminiKey = false, deviceUsable = true, offlineReady = true))
    }

    @Test
    fun onlyAutoRetriesOfflineAfterANetworkError() {
        assertTrue(SpeechRoutes.retryOfflineAfterNetworkError(SpeechInput.Auto, offlineReady = true))
        assertFalse(SpeechRoutes.retryOfflineAfterNetworkError(SpeechInput.Auto, offlineReady = false))
        assertFalse(SpeechRoutes.retryOfflineAfterNetworkError(SpeechInput.Device, offlineReady = true))
    }

    @Test
    fun pcmBecomesSignedSamples() {
        // Little-endian 16-bit: 0, 16384, -32768, 32767, -1.
        val pcm = byteArrayOf(0, 0, 0, 0x40, 0, 0x80.toByte(), 0xFF.toByte(), 0x7F, 0xFF.toByte(), 0xFF.toByte())
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f, 32767 / 32768f, -1 / 32768f), SpeechRoutes.samples(pcm), 1e-6f)
        assertEquals(1, SpeechRoutes.samples(byteArrayOf(1, 0, 9)).size) // a stray odd byte is dropped
    }

    @Test
    fun transcriptsAreTidied() {
        assertEquals("What's the coolant temperature?", SpeechRoutes.clean("  What's the coolant   temperature? "))
        assertEquals("", SpeechRoutes.clean("[BLANK_AUDIO]"))
        assertEquals("find gas", SpeechRoutes.clean("<|en|> find gas"))
    }
}
