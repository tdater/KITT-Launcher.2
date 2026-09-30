package com.aiconsole.knightriderlauncher.stream

/** A one-tap site in the stream window's picker. */
data class StreamSite(val label: String, val url: String)

/**
 * Built-in picks are licensed services that are free to watch or listen to (ad-supported,
 * library card, or free tier) and play in a browser. The driver's own saved sites are
 * stored separately and can be anything they choose.
 */
object StreamSites {

    val video = listOf(
        StreamSite("YouTube", "https://www.youtube.com/"),
        StreamSite("Tubi", "https://tubitv.com/"),
        StreamSite("Pluto TV", "https://pluto.tv/"),
        StreamSite("Plex", "https://watch.plex.tv/"),
        StreamSite("Roku Channel", "https://therokuchannel.roku.com/"),
        StreamSite("Xumo Play", "https://play.xumo.com/"),
        StreamSite("Crackle", "https://www.crackle.com/"),
        StreamSite("Fawesome", "https://fawesome.tv/"),
        StreamSite("Samsung TV Plus", "https://www.samsungtvplus.com/"),
        StreamSite("Sling Freestream", "https://watch.sling.com/"),
        StreamSite("Shout! TV", "https://www.shoutfactorytv.com/"),
        StreamSite("RetroCrush", "https://www.retrocrush.tv/"),
        StreamSite("YouTube Movies", "https://www.youtube.com/feed/storefront"),
        StreamSite("Kanopy", "https://www.kanopy.com/"),
        StreamSite("Hoopla", "https://www.hoopladigital.com/"),
        StreamSite("PBS", "https://www.pbs.org/video/"),
        StreamSite("Twitch", "https://www.twitch.tv/"),
        StreamSite("Dailymotion", "https://www.dailymotion.com/"),
        StreamSite("Vimeo", "https://vimeo.com/"),
        StreamSite("Internet Archive", "https://archive.org/details/movies")
    )

    val audio = listOf(
        StreamSite("YT Music", "https://music.youtube.com/"),
        StreamSite("Spotify", "https://open.spotify.com/"),
        StreamSite("SoundCloud", "https://soundcloud.com/"),
        StreamSite("Pandora", "https://www.pandora.com/"),
        StreamSite("iHeartRadio", "https://www.iheart.com/"),
        StreamSite("TuneIn", "https://tunein.com/"),
        StreamSite("Amazon Music", "https://music.amazon.com/"),
        StreamSite("Deezer", "https://www.deezer.com/"),
        StreamSite("Audiomack", "https://audiomack.com/"),
        StreamSite("Mixcloud", "https://www.mixcloud.com/"),
        StreamSite("Bandcamp", "https://bandcamp.com/"),
        StreamSite("AccuRadio", "https://www.accuradio.com/"),
        StreamSite("Live365", "https://live365.com/"),
        StreamSite("SomaFM", "https://somafm.com/"),
        StreamSite("Radio Garden", "https://radio.garden/"),
        StreamSite("Jamendo", "https://www.jamendo.com/"),
        StreamSite("Free Music Archive", "https://freemusicarchive.org/")
    )

    /** Saved sites, one per line: label|url. */
    fun decode(text: String): List<StreamSite> = text.lines().mapNotNull { line ->
        val i = line.lastIndexOf('|')
        if (i <= 0) return@mapNotNull null
        val url = StreamUrls.normalize(line.substring(i + 1)) ?: return@mapNotNull null
        StreamSite(line.substring(0, i).trim().ifEmpty { StreamUrls.titleOf(url) }, url)
    }.distinctBy { it.url }

    fun encode(sites: List<StreamSite>): String =
        sites.distinctBy { it.url }.joinToString("\n") { "${it.label.replace("|", "/").replace("\n", " ").take(40)}|${it.url}" }
}
