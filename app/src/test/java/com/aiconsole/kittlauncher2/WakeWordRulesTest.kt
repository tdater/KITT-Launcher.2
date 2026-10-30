package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.WakeWordRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordRulesTest {

    private fun listen(mediaPlaying: Boolean = false, enabled: Boolean = true, kittSpeaking: Boolean = false) =
        WakeWordRules.shouldListen(
            enabled = enabled,
            visible = true,
            suspended = false,
            kittSpeaking = kittSpeaking,
            micBlocked = false,
            mediaPlaying = mediaPlaying,
            micPermission = true
        )

    @Test
    fun neverListensOverMusic() {
        assertTrue(listen())
        assertFalse(listen(mediaPlaying = true))
    }

    @Test
    fun staysOffWhenDisabledOrKittIsTalking() {
        assertFalse(listen(enabled = false))
        assertFalse(listen(kittSpeaking = true))
    }

    @Test
    fun quietSessionsRestartCalmlyAndErrorsBackOff() {
        assertEquals(1_000L, WakeWordRules.restartDelayMs(error = false, failures = 0))
        assertEquals(500L, WakeWordRules.restartDelayMs(error = true, failures = 1))
        assertEquals(15_000L, WakeWordRules.restartDelayMs(error = true, failures = 100))
    }

    @Test
    fun startupAndPostSpeechCooldownPreventSystemRecognizerChimeLoops() {
        // Offline spotter needs no cooldown
        assertEquals(0L, WakeWordRules.cooldownRemainingMs(nowElapsedMs = 500L, createdAtMs = 0L, lastSpokeAtMs = 400L, useOffline = true))
        // System recognizer waits out startup grace window (3,500 ms)
        assertEquals(2_500L, WakeWordRules.cooldownRemainingMs(nowElapsedMs = 1_000L, createdAtMs = 0L, lastSpokeAtMs = 0L, useOffline = false))
        // System recognizer waits out post-speech cooldown (1,500 ms)
        assertEquals(1_000L, WakeWordRules.cooldownRemainingMs(nowElapsedMs = 5_500L, createdAtMs = 0L, lastSpokeAtMs = 5_000L, useOffline = false))
        // After cooldowns elapse, 0 ms wait
        assertEquals(0L, WakeWordRules.cooldownRemainingMs(nowElapsedMs = 7_000L, createdAtMs = 0L, lastSpokeAtMs = 5_000L, useOffline = false))

        // Offline spotter continues indefinitely; system recognizer caps consecutive silent chime sessions
        assertTrue(WakeWordRules.shouldContinueAfterQuietSession(useOffline = true, quietSessions = 10))
        assertTrue(WakeWordRules.shouldContinueAfterQuietSession(useOffline = false, quietSessions = 0))
        assertFalse(WakeWordRules.shouldContinueAfterQuietSession(useOffline = false, quietSessions = WakeWordRules.MAX_CHIME_SESSIONS))
    }
}
