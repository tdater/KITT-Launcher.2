package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.stream.Userscript
import com.aiconsole.knightriderlauncher.stream.Userscripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptsTest {

    private val sample = """
        // ==UserScript==
        // @name         YTM Tweaks
        // @namespace    https://example.org
        // @version      1.2
        // @description  Does things
        // @match        https://music.youtube.com/*
        // @match        *://*.youtube.com/watch*
        // @exclude      https://music.youtube.com/library*
        // @run-at       document-start
        // @grant        none
        // @noframes
        // ==/UserScript==
        console.log("hi");
    """.trimIndent()

    @Test
    fun parsesMetadata() {
        val s = Userscripts.parse(sample)!!
        assertEquals("YTM Tweaks", s.name)
        assertEquals("1.2", s.version)
        assertEquals(listOf("https://music.youtube.com/*", "*://*.youtube.com/watch*"), s.matches)
        assertEquals(listOf("https://music.youtube.com/library*"), s.excludes)
        assertEquals(Userscript.RunAt.DocumentStart, s.runAt)
        assertTrue(s.noFrames)
        assertEquals("https-example-org-ytm-tweaks", s.id)
    }

    @Test
    fun rejectsScriptsWithoutHeaderOrName() {
        assertNull(Userscripts.parse("console.log(1)"))
        assertNull(Userscripts.parse("// ==UserScript==\n// @version 1\n// ==/UserScript=="))
    }

    @Test
    fun matchPatternsFollowGreasemonkeyRules() {
        val s = Userscripts.parse(sample)!!
        assertTrue(Userscripts.appliesTo(s, "https://music.youtube.com/watch?v=abc"))
        assertTrue(Userscripts.appliesTo(s, "https://www.youtube.com/watch?v=abc"))
        assertTrue(Userscripts.appliesTo(s, "http://youtube.com/watch?v=abc"))
        assertFalse(Userscripts.appliesTo(s, "https://music.youtube.com/library/playlists"))
        assertFalse(Userscripts.appliesTo(s, "https://evil-youtube.com/watch"))
        assertFalse(Userscripts.appliesTo(s, "https://www.youtube.com/feed"))
    }

    @Test
    fun includeGlobsRegexesAndDefaults() {
        assertEquals("^https://a\\.com/.*$", Userscripts.includeRegex("https://a.com/*"))
        assertEquals("^https?://b\\.org/x", Userscripts.includeRegex("/^https?://b\\.org/x/"))
        val none = Userscripts.parse("// ==UserScript==\n// @name All\n// ==/UserScript==\n")!!
        assertTrue(Userscripts.appliesTo(none, "https://anything.example/path"))
        assertNotNull(Userscripts.matchPatternRegex("<all_urls>"))
        assertNull(Userscripts.matchPatternRegex("not a pattern"))
    }

    @Test
    fun wrapperGuardsUrlAndRunsOnce() {
        val js = Userscripts.wrap(Userscripts.parse(sample)!!)
        assertTrue(js.contains("new RegExp(\"^https://music\\\\.youtube\\\\.com/.*$\")"))
        assertTrue(js.contains("if (window.top !== window.self) return;"))
        assertTrue(js.contains("done[\"https-example-org-ytm-tweaks\"] = true;"))
        assertTrue(js.contains("console.log(\"hi\");"))
        assertTrue(js.trimEnd().endsWith("run();\n})();") || js.contains("  run();"))
    }
}
