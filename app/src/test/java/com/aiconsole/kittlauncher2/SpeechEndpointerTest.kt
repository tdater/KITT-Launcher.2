package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.SpeechEndpointer
import com.aiconsole.kittlauncher2.assistant.SpeechEndpointer.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechEndpointerTest {

    private fun run(e: SpeechEndpointer, db: Double, frames: Int): Decision {
        var last = Decision.Continue
        repeat(frames) {
            last = e.feed(db)
            if (last != Decision.Continue) return last
        }
        return last
    }

    @Test
    fun speechThenSilenceEndsTheRecording() {
        val e = SpeechEndpointer()
        assertEquals(Decision.Continue, run(e, -50.0, 15)) // road noise floor
        assertEquals(Decision.Continue, run(e, -25.0, 50)) // a second of speech
        assertEquals(Decision.Done, run(e, -49.0, 100)) // quiet again
        assertTrue(e.spoke)
    }

    @Test
    fun noSpeechTimesOut() {
        val e = SpeechEndpointer()
        assertEquals(Decision.NoSpeech, run(e, -50.0, 1_000))
    }

    @Test
    fun loudCabinNeedsSpeechAboveItsOwnFloor() {
        val e = SpeechEndpointer()
        run(e, -30.0, 15) // noisy cab
        assertEquals(Decision.Continue, run(e, -26.0, 10)) // not enough above the floor
        assertEquals(Decision.Continue, run(e, -15.0, 20)) // clearly speech
        assertEquals(Decision.Done, run(e, -30.0, 100))
    }

    @Test
    fun levelOfSilenceAndFullScale() {
        assertEquals(-90.0, SpeechEndpointer.levelDb(ShortArray(160), 160), 0.0)
        val loud = ShortArray(160) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        assertTrue(SpeechEndpointer.levelDb(loud, 160) > -0.5)
    }
}
