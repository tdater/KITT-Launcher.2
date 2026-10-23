package com.aiconsole.kittlauncher2.media

/** One entry from a music app's library: a playlist, artist, album, track… */
data class LibraryItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val browsable: Boolean,
    val playable: Boolean,
    val iconUri: String?
)

/** A step down the library tree (Spotify › Playlists › Road trip). */
data class Crumb(val id: String, val title: String)

/** What tapping an item does. */
enum class TapAction { Open, Play }

/** Pure rules for the Media page's library browser (the Android side is [MediaLibrary]). */
object MediaBrowseRules {

    const val MAX_ITEMS = 300
    const val MAX_QUERY = 200

    /** Folders (playlists, artists, albums) open; a plain track plays. Both: it opens, with a Play button beside it. */
    fun tapAction(item: LibraryItem): TapAction =
        if (item.browsable) TapAction.Open else TapAction.Play

    /** The search box text to send to the music app, or null when there is nothing to search for. */
    fun cleanQuery(raw: String): String? = raw.trim().take(MAX_QUERY).ifBlank { null }

    /** The app playing now first, then the one used last time, then A–Z. */
    fun <T> order(items: List<T>, packageOf: (T) -> String, labelOf: (T) -> String, nowPlaying: String?, lastUsed: String?): List<T> =
        items.sortedWith(
            compareBy<T>(
                { if (packageOf(it) == nowPlaying) 0 else 1 },
                { if (packageOf(it) == lastUsed) 0 else 1 },
                { labelOf(it).lowercase() }
            )
        )

    /** Power-of-two downsample so a cover loads near [target] px instead of at full size. */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }
}

/** The way down the tree. The first crumb is the app's root. */
data class BrowsePath(val crumbs: List<Crumb> = emptyList()) {
    val current: Crumb? get() = crumbs.lastOrNull()
    val canGoUp: Boolean get() = crumbs.size > 1

    fun push(crumb: Crumb) = BrowsePath(crumbs + crumb)

    fun pop() = if (canGoUp) BrowsePath(crumbs.dropLast(1)) else this

    /** "Spotify › Playlists › Road trip", shortened to the last two steps when it gets long. */
    fun line(): String {
        val titles = crumbs.map { it.title }
        return if (titles.size > 3) "… › " + titles.takeLast(2).joinToString(" › ") else titles.joinToString(" › ")
    }
}

/** Something the driver played from the Library tab or by voice, kept so it is one tap away next time. */
data class RecentPlay(
    val sourcePackage: String,
    val sourceLabel: String,
    /** The item's media id, or null when it was a search. */
    val mediaId: String?,
    /** The search text, when it was a search. */
    val query: String?,
    val title: String,
    val subtitle: String?,
    val iconUri: String?
) {
    /** The same thing, played again: same app and same item or search. */
    val key: String get() = "$sourcePackage|${mediaId ?: ""}|${query ?: ""}"
}

/** Pure list and storage rules for [RecentPlay] (stored as text; org.json isn't available to JVM tests). */
object RecentPlays {

    const val MAX = 12
    private const val FIELD = '\u001F'
    private const val RECORD = '\u001E'

    /** [entry] goes first; an earlier copy of it is dropped; the list is cut to [MAX]. */
    fun push(list: List<RecentPlay>, entry: RecentPlay): List<RecentPlay> =
        (listOf(entry) + list.filterNot { it.key == entry.key }).take(MAX)

    /** The recents shown for [sourcePackage], or every app's when it is null. */
    fun forSource(list: List<RecentPlay>, sourcePackage: String?): List<RecentPlay> =
        if (sourcePackage == null) list else list.filter { it.sourcePackage == sourcePackage }

    fun encode(list: List<RecentPlay>): String = list.joinToString(RECORD.toString()) { r ->
        listOf(r.sourcePackage, r.sourceLabel, r.mediaId, r.query, r.title, r.subtitle, r.iconUri)
            .joinToString(FIELD.toString()) { clean(it) }
    }

    fun decode(text: String): List<RecentPlay> = text.split(RECORD).mapNotNull { record ->
        val f = record.split(FIELD)
        if (f.size != 7 || f[0].isEmpty() || f[4].isEmpty()) return@mapNotNull null
        RecentPlay(f[0], f[1], f[2].ifEmpty { null }, f[3].ifEmpty { null }, f[4], f[5].ifEmpty { null }, f[6].ifEmpty { null })
    }.take(MAX)

    private fun clean(value: String?) = value.orEmpty().replace(FIELD, ' ').replace(RECORD, ' ')
}
