package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.data.AppLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListsTest {

    @Test
    fun encodeDecodeRoundTripsAndDropsBlanksAndDuplicates() {
        assertEquals(listOf("a/b", "c/d"), AppLists.decode("a/b\n\n c/d \na/b"))
        assertEquals("a/b\nc/d", AppLists.encode(listOf("a/b", "c/d", "a/b")))
        assertEquals(emptyList<String>(), AppLists.decode(""))
    }

    @Test
    fun toggleAddsAtTheEndOrRemoves() {
        assertEquals(listOf("x", "y"), AppLists.toggle(listOf("x"), "y"))
        assertEquals(listOf("y"), AppLists.toggle(listOf("x", "y"), "x"))
    }

    @Test
    fun sectionsBucketNonLettersUnderHash() {
        assertEquals('M', AppLists.sectionOf("maps"))
        assertEquals('#', AppLists.sectionOf("7-Zip"))
        assertEquals('#', AppLists.sectionOf("  "))
        assertEquals('#', AppLists.sectionOf("Ärger"))
    }

    @Test
    fun searchMatchesEveryWordInAnyOrder() {
        assertTrue(AppLists.matches("YouTube Music", "music you"))
        assertTrue(AppLists.matches("Waze", ""))
        assertFalse(AppLists.matches("YouTube Music", "music spotify"))
    }

    @Test
    fun homeShowsPinnedInOrderElseVisibleApps() {
        val apps = listOf("a", "b", "c")
        assertEquals(listOf("c", "a"), AppLists.homeSelection(apps, { it }, pinned = listOf("c", "gone", "a"), hidden = setOf("c")))
        assertEquals(listOf("a", "c"), AppLists.homeSelection(apps, { it }, pinned = emptyList(), hidden = setOf("b")))
    }

    @Test
    fun railJumpFindsFirstAppInSection() {
        val labels = listOf("1Weather", "Android Auto", "Maps", "Music")
        assertEquals(0, AppLists.firstIndexOf(labels, '#'))
        assertEquals(2, AppLists.firstIndexOf(labels, 'M'))
        assertNull(AppLists.firstIndexOf(labels, 'Z'))
    }
}
