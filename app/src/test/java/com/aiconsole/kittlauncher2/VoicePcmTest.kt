package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.VoicePcm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePcmTest {

    @Test
    fun floatsBecomeClippedPcm16() {
        val pcm = VoicePcm.toPcm16(floatArrayOf(0f, 1f, -1f, 0.5f, 2f, -3f))
        assertArrayEquals(shortArrayOf(0, 32767, -32767, 16384, 32767, -32767), pcm)
    }

    @Test
    fun frozenPlayPositionIsAStall() {
        val watch = VoicePcm.StallWatch(1_500)
        assertFalse(watch.stalled(0, 0))
        assertFalse(watch.stalled(0, 1_000))
        assertFalse(watch.stalled(480, 1_200)) // Moving: the clock restarts.
        assertFalse(watch.stalled(480, 2_600))
        // The head unit in the 23 Oct report: a few ms delivered, then nothing.
        assertTrue(watch.stalled(480, 2_700))
    }
}
