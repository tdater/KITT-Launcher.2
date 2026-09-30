package com.aiconsole.knightriderlauncher.assistant

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Plain-Kotlin half of KITT's neural voice (Gemini speech generation via the Interactions API):
 * request JSON, text clean-up and chunking, cache keys and WAV framing. Unit-tested on the JVM.
 */
object GeminiSpeechProtocol {

    /** Newest first; a 404 moves on to the next, so a retired model doesn't silence KITT. */
    val MODELS = listOf("gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts", "gemini-3.1-flash-tts-preview")

    /** Calm male prebuilt voices worth auditioning for KITT, with Google's one-word description. */
    val VOICES = listOf(
        "Iapetus" to "Clear",
        "Charon" to "Informative",
        "Sadaltager" to "Knowledgeable",
        "Algieba" to "Smooth",
        "Orus" to "Firm",
        "Rasalgethi" to "Informative",
        "Schedar" to "Even"
    )
    const val DEFAULT_VOICE = "Iapetus"

    /** Delivery direction: the character of a talking-car AI, not an imitation of any actor. */
    const val STYLE = "A calm, articulate artificial intelligence built into a 1980s high-tech sports car. " +
        "Crisp, precise American diction; smooth mid-range male tone; measured, unhurried pacing; " +
        "dry, understated wit and unflappable, faintly superior composure. Never shouty or robotic."

    /** Urgent alerts keep the character but tighten the delivery. */
    const val URGENT_STYLE = "$STYLE For this line: firm, focused and urgent, still controlled."

    const val SAMPLE_RATE = 24_000
    private const val MAX_CHUNK = 280

    fun requestBody(model: String, text: String, voice: String, style: String = STYLE): String =
        """{"model":${quote(model)},"input":[{"type":"user_input","content":[{"type":"text","text":${quote(text)},""" +
            """"annotations":[{"type":"speech_metadata","style":${quote(style)}}]}]}],""" +
            """"response_format":{"type":"audio"},"generation_config":{"speech_config":[{"voice":${quote(voice)}}]}}"""

    /** Strips markdown and links that would otherwise be read out literally. */
    fun speakable(text: String): String = text
        .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1") // [label](url) -> label
        .replace(Regex("""https?://\S+"""), "")
        .replace(Regex("""(?m)^\s*(#{1,6}|[-*•]|\d+\.)\s+"""), "")
        .replace(Regex("""[*_`#]+"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * Splits into sentence-aligned chunks of at most [maxChars] so the first one can play while
     * the next is synthesised. A single over-long sentence is split at word boundaries.
     */
    fun chunks(text: String, maxChars: Int = MAX_CHUNK): List<String> {
        val sentences = text.split(Regex("""(?<=[.!?])\s+""")).filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) out += current.toString().trim()
            current.setLength(0)
        }
        for (sentence in sentences.flatMap { splitLong(it, maxChars) }) {
            if (current.isNotEmpty() && current.length + 1 + sentence.length > maxChars) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        flush()
        return out
    }

    private fun splitLong(sentence: String, maxChars: Int): List<String> {
        if (sentence.length <= maxChars) return listOf(sentence)
        val parts = mutableListOf<String>()
        val part = StringBuilder()
        for (word in sentence.split(' ')) {
            if (part.isNotEmpty() && part.length + 1 + word.length > maxChars) {
                parts += part.toString()
                part.setLength(0)
            }
            if (part.isNotEmpty()) part.append(' ')
            part.append(word)
        }
        if (part.isNotEmpty()) parts += part.toString()
        return parts
    }

    /** Cache file name for one spoken chunk. The model isn't part of it: any model's take is fine. */
    fun cacheKey(voice: String, style: String, text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$voice\u0000$style\u0000$text".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun isWav(bytes: ByteArray): Boolean =
        bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE"

    /** Wraps headerless 16-bit mono PCM (the API's `audio/l16`) so MediaPlayer can play it. */
    fun wavFromPcm(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM fmt chunk size
            putShort(1) // PCM
            putShort(1) // mono
            putInt(sampleRate)
            putInt(sampleRate * 2) // byte rate
            putShort(2) // block align
            putShort(16) // bits per sample
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }
        return header.array() + pcm
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
