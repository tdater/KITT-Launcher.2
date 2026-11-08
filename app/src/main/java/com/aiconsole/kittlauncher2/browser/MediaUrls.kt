package com.aiconsole.kittlauncher2.browser

/** Which links are a bare video file or stream (pure, unit-tested), so the native player can take them instead of a web page. */
object MediaUrls {
    private val EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "m3u8", "mpd")

    /** True for an http(s) link whose path ends in a video container or stream playlist; the query string is ignored. */
    fun isDirectVideo(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val path = lower.substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/')
        if (!name.contains('.')) return false
        return name.substringAfterLast('.') in EXTENSIONS
    }

    /**
     * Whether a navigation goes to the native player. Only a video link the driver tapped, in the tab on screen, that isn't from
     * a blocked ad host, and that wasn't just sent back to the page. An ad redirect or a script's untapped hop is left to the
     * browser's own ad-block and pop-up rules, so it can't start playing on its own.
     */
    fun shouldPlayNatively(isMainFrame: Boolean, url: String, loadInPage: String?, adHost: Boolean, tapped: Boolean, currentTab: Boolean): Boolean =
        isMainFrame && url != loadInPage && isDirectVideo(url) && !adHost && tapped && currentTab

    /** "m:ss" or "h:mm:ss" for a position in milliseconds. */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
