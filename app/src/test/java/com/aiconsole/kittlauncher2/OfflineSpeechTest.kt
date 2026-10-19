package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.OfflineHeard
import com.aiconsole.kittlauncher2.assistant.OfflinePhrases
import com.aiconsole.kittlauncher2.assistant.PieceTokenizer
import com.aiconsole.kittlauncher2.assistant.VoiceCommands
import com.aiconsole.kittlauncher2.nav.OfflineMapRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSpeechTest {

    /** The offline keyword model's own sentencepiece file (test resource, from its release archive). */
    private val tokenizer by lazy {
        PieceTokenizer.fromModel(javaClass.getResourceAsStream("/kws/bpe.model")!!.readBytes())
    }

    @Test
    fun tokenizerMatchesTheModelsOwnKeywordExamples() {
        // keywords.txt / keywords_raw.txt shipped with sherpa-onnx-kws-zipformer-gigaspeech-3.3M.
        val examples = mapOf(
            "HELLO WORLD" to "▁HE LL O ▁WORLD",
            "HI GOOGLE" to "▁HI ▁GO O G LE",
            "HEY SIRI" to "▁HE Y ▁S I RI",
            "ALEXA" to "▁A LE X A",
            "LOVE AND PEACE" to "▁LOVE ▁AND ▁P E A CE",
            "PLAY MUSIC" to "▁PLAY ▁MU S IC",
            "GO HOME" to "▁GO ▁HOME",
            "HAPPY NEW YEAR" to "▁HA PP Y ▁NEW ▁YEAR",
            "MERRY CHRISTMAS" to "▁ME R RY ▁ CH R IST MA S",
            "LIGHT UP" to "▁ L IGHT ▁UP",
            "FOREVER" to "▁FOR E VER"
        )
        examples.forEach { (text, pieces) -> assertEquals(text, pieces, tokenizer.encode(text)) }
        assertNotNull(tokenizer.encode("hey kitt"))
    }

    @Test
    fun keywordsCoverWakeAndEveryCommand() {
        val file = OfflinePhrases.keywords(tokenizer::encode)
        val lines = file.lines()
        assertTrue(lines.any { it.endsWith("@WAKE") })
        OfflinePhrases.COMMANDS.indices.forEach { i -> assertTrue("command $i", lines.any { it.endsWith("@CMD_$i") }) }
        assertTrue(lines.all { it.substringBefore(" @").isNotBlank() })
    }

    @Test
    fun everyOfflineCommandIsOneTheLauncherRuns() {
        OfflinePhrases.COMMANDS.forEach { (phrase, text) -> assertNotNull(phrase, VoiceCommands.parse(text)) }
        assertEquals(OfflineHeard.Wake, OfflinePhrases.heard("WAKE"))
        assertEquals(OfflineHeard.Command("pause"), OfflinePhrases.heard("@CMD_0"))
        assertNull(OfflinePhrases.heard("CMD_999"))
        assertNull(OfflinePhrases.heard("something"))
    }

    @Test
    fun offlineMapFileNames() {
        assertEquals("ohio.mbtiles", OfflineMapRules.safeName("ohio.mbtiles"))
        assertEquals("my_map.sqlite", OfflineMapRules.safeName("/storage/Download/my*map.sqlite"))
        assertNull(OfflineMapRules.safeName("notes.txt"))
        assertNull(OfflineMapRules.safeName(".hidden.mbtiles"))
        assertTrue(OfflineMapRules.isMapFile("Area.GEMF"))
        assertFalse(OfflineMapRules.isMapFile("cache.db"))
    }
}
