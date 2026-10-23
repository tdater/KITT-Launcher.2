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
