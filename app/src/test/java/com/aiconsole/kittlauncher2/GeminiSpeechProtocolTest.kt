package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.GeminiSpeechProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GeminiSpeechProtocolTest {

    @Test
    fun requestBodyEscapesTextAndCarriesVoiceAndStyle() {
        val body = GeminiSpeechProtocol.requestBody("gemini-3.8-flash-tts", "He said \"hi\"\nthen\\left", "Iapetus", "calm")
        assertTrue(body.contains("\"model\":\"gemini-3.8-flash-tts\""))
        assertTrue(body.contains("\"text\":\"He said \\\"hi\\\"\\nthen\\\\left\""))
        assertTrue(body.contains("\"style\":\"calm\""))
        assertTrue(body.contains("\"speech_config\":[{\"voice\":\"Iapetus\"}]"))
        assertTrue(body.contains("\"response_format\":{\"type\":\"audio\"}"))
    }

    @Test
    fun speakableStripsMarkdownAndLinks() {
        val text = "## Status\n- **Coolant** is `92°C`\n1. See [the manual](https://example.com/x) or https://example.com"
        assertEquals("Status Coolant is 92°C See the manual or", GeminiSpeechProtocol.speakable(text))
    }

    @Test
    fun chunksKeepSentencesTogetherUnderTheLimit() {
        val chunks = GeminiSpeechProtocol.chunks("One two. Three four! Five six?", maxChars = 20)
        assertEquals(listOf("One two. Three four!", "Five six?"), chunks)
    }

    @Test
    fun chunksSplitAnOverlongSentenceAtWords() {
        val chunks = GeminiSpeechProtocol.chunks("alpha beta gamma delta epsilon", maxChars = 12)
        assertEquals(listOf("alpha beta", "gamma delta", "epsilon"), chunks)
        assertTrue(chunks.all { it.length <= 12 })
    }

    @Test
    fun chunksOfBlankTextAreEmpty() {
        assertEquals(emptyList<String>(), GeminiSpeechProtocol.chunks("   "))
    }

    @Test
    fun cacheKeyDependsOnVoiceStyleAndText() {
        val base = GeminiSpeechProtocol.cacheKey("Iapetus", "calm", "Hello")
        assertEquals(64, base.length)
        assertEquals(base, GeminiSpeechProtocol.cacheKey("Iapetus", "calm", "Hello"))
        assertNotEquals(base, GeminiSpeechProtocol.cacheKey("Charon", "calm", "Hello"))
        assertNotEquals(base, GeminiSpeechProtocol.cacheKey("Iapetus", "urgent", "Hello"))
        assertNotEquals(base, GeminiSpeechProtocol.cacheKey("Iapetus", "calm", "Hello."))
    }

    @Test
    fun wavFromPcmWritesAValidHeader() {
        val pcm = ByteArray(480) { it.toByte() }
        val wav = GeminiSpeechProtocol.wavFromPcm(pcm, 24_000)
        assertEquals(44 + pcm.size, wav.size)
        assertTrue(GeminiSpeechProtocol.isWav(wav))
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + pcm.size, header.getInt(4))
        assertEquals(1.toShort(), header.getShort(20)) // PCM
        assertEquals(1.toShort(), header.getShort(22)) // mono
        assertEquals(24_000, header.getInt(24))
        assertEquals(48_000, header.getInt(28))
        assertEquals(16.toShort(), header.getShort(34))
        assertEquals(pcm.size, header.getInt(40))
    }

    @Test
    fun rawPcmIsNotMistakenForWav() {
        assertFalse(GeminiSpeechProtocol.isWav(ByteArray(64)))
    }
}
