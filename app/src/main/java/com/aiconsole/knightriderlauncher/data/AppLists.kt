package com.aiconsole.knightriderlauncher.data

/**
 * Plain-Kotlin rules for the app drawer and home favourites: stored key lists, the A-Z rail and
 * search. Apps are identified by [LauncherApp.key] ("package/activity").
 */
object AppLists {

    /** Rail entries: '#' for anything not starting with a letter, then A-Z. */
    val RAIL: List<Char> = listOf('#') + ('A'..'Z')

    fun decode(stored: String): List<String> = stored.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun encode(keys: List<String>): String = keys.distinct().joinToString("\n")

    /** Adds [key] at the end, or removes it if present. */
    fun toggle(keys: List<String>, key: String): List<String> = if (key in keys) keys - key else keys + key

    fun sectionOf(label: String): Char {
        val first = label.trimStart().firstOrNull()?.uppercaseChar() ?: return '#'
        return if (first in 'A'..'Z') first else '#'
    }

    /** Matches every word of [query] against the label, ignoring case and word order. */
    fun matches(label: String, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val text = label.lowercase()
        return words.all { it in text }
    }

    /**
     * Apps for the home grid: the pinned favourites in pinned order when there are any,
     * otherwise every app that isn't hidden.
     */
    fun <T> homeSelection(apps: List<T>, key: (T) -> String, pinned: List<String>, hidden: Set<String>): List<T> {
        if (pinned.isEmpty()) return apps.filter { key(it) !in hidden }
        val byKey = apps.associateBy(key)
        return pinned.mapNotNull { byKey[it] }
    }

    /** Index of the first label in [labels] (already sorted) that belongs to [section], or null. */
    fun firstIndexOf(labels: List<String>, section: Char): Int? =
        labels.indexOfFirst { sectionOf(it) == section }.takeIf { it >= 0 }
}
