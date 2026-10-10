package com.aiconsole.kittlauncher2.stream

/** Address and user-agent rules for the stream window. Plain Kotlin. */
object StreamUrls {

    /** Turns what the driver typed into a loadable address, or null if it isn't one. */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        return when {
            text.startsWith("https://", ignoreCase = true) || text.startsWith("http://", ignoreCase = true) -> text
            '.' in text -> "https://$text"
            else -> null
        }
    }

    /** Short title for the window header, e.g. "twitch.tv". */
    fun titleOf(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix("www.").removePrefix("m.").ifEmpty { "Stream" }

    /** Links the window offers to install as userscripts instead of opening. */
    fun isUserscript(url: String): Boolean = url.substringBefore('?').substringBefore('#').endsWith(".user.js", ignoreCase = true)

    /**
     * Desktop Chrome user agent built from WebView's own Chrome version, so sites serve their full
     * desktop players (e.g. YouTube Music's video view and audio-quality setting).
     */
    fun desktopUserAgent(webViewUserAgent: String): String {
        val chrome = Regex("""Chrome/([\d.]+)""").find(webViewUserAgent)?.groupValues?.get(1) ?: "130.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }
}
