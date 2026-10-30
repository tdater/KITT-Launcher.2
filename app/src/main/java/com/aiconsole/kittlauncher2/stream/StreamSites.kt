package com.aiconsole.kittlauncher2.stream

import com.aiconsole.kittlauncher2.browser.HomeSite

/** A one-tap site in the stream window's picker. */
data class StreamSite(val label: String, val url: String)

/**
 * Built-in picks are organized by the driver's curated bookmark categories first (Free, licensed or
 * public-domain options; Pick a song; Free radio and stations; Independent music and DJ mixes;
 * Streaming guides and aggregators; Paid or mixed services; Other sites named in the articles),
 * followed by additional built-in streaming services. The driver's own saved sites are stored
 * separately and can be anything they choose.
 */
object StreamSites {

    /** Live Sports Streams from Piradex. */
    val PIRADEX_LIVE_SPORTS: List<HomeSite> = listOf(
        HomeSite("StreamEast", "https://v2.streameast.ga/", "Top live sports streams, NFL, NBA, MLB & NHL", 0xFF00796B, listOf("Live Sports", "Sports")),
        HomeSite("MethStreams", "https://methstreams.ms/", "Live sports portals, combat and racing", 0xFF0097A7, listOf("Live Sports", "Sports")),
        HomeSite("LiveTV", "https://livetv.sx/enx/", "Live sports broadcasts, video highlights and scores", 0xFFD32F2F, listOf("Live Sports", "Sports")),
        HomeSite("Sportsurge", "https://v2.sportsurge.net/", "Multi-sport stream index: NBA, NFL, MMA & racing", 0xFFE53935, listOf("Live Sports", "Sports")),
        HomeSite("CrackStreams", "https://crackstreams.io/", "Combat sports, MMA, UFC, boxing and basketball", 0xFFFFA000, listOf("Live Sports", "Sports")),
        HomeSite("Streamed.pk", "https://streamed.pk/", "High quality live sports event streams", 0xFFE65100, listOf("Live Sports", "Sports")),
        HomeSite("StreamSports99", "https://streamsports99.su/", "Daily live sports matches and schedules", 0xFF0288D1, listOf("Live Sports", "Sports")),
        HomeSite("NTV Sports", "https://ntv.cx/", "Live sports events, feeds and schedules", 0xFF7B1FA2, listOf("Live Sports", "Sports")),
        HomeSite("OnHockey", "https://onhockey.tv/", "Live ice hockey games, NHL and world leagues", 0xFF0277BD, listOf("Live Sports", "Sports")),
        HomeSite("Score808", "https://a1.score808hd.tv/", "HD football and sports streaming matches", 0xFF303F9F, listOf("Live Sports", "Sports")),
        HomeSite("FCTV33", "https://www.fctv33.hair/", "Football, soccer and combat sports streams", 0xFF1976D2, listOf("Live Sports", "Sports")),
        HomeSite("StreamFree", "https://streamfree.top/", "Free sports streams and worldwide events", 0xFF388E3C, listOf("Live Sports", "Sports")),
        HomeSite("BinTV", "https://www.bintv.cc/", "Live sports channels and match broadcasts", 0xFF512DA8, listOf("Live Sports", "Sports")),
        HomeSite("SportyHunter", "https://sportyhunter.xyz/", "Daily live match streams and score updates", 0xFFF57C00, listOf("Live Sports", "Sports")),
        HomeSite("RoxieStreams", "https://roxiestreams.su/", "Live hockey, soccer, combat and racing", 0xFFC2185B, listOf("Live Sports", "Sports")),
        HomeSite("TimStreams", "https://timstreams.st/", "Live sports streams and tournament coverage", 0xFF5D4037, listOf("Live Sports", "Sports")),
        HomeSite("StrikeOut", "https://strikeout.im/", "MLB, NFL, NBA and international sports feeds", 0xFFD32F2F, listOf("Live Sports", "Sports")),
        HomeSite("WatchFooty", "https://www.watchfooty.st/en", "Live football, soccer tournaments and cup matches", 0xFF2E7D32, listOf("Live Sports", "Sports"))
    )

    /** Live TV Channels from Piradex. */
    val PIRADEX_LIVE_TV: List<HomeSite> = listOf(
        HomeSite("DaddyLive HD", "https://dlhd.st/24-7-channels.php", "24/7 live cable, news and sports networks", 0xFF6A1B9A, listOf("Live TV")),
        HomeSite("GlobeTV", "https://globetv.app/", "Worldwide live television networks and news", 0xFF1565C0, listOf("Live TV")),
        HomeSite("TV247 US", "https://tv247us.live/", "US broadcast television and live channels", 0xFF00838F, listOf("Live TV")),
        HomeSite("StreamSports99 TV", "https://streamsports99.su/live-tv", "Live TV channels and sports network feeds", 0xFF0288D1, listOf("Live TV")),
        HomeSite("NTV Live TV", "https://ntv.cx/#livetv", "24/7 live television channels and streams", 0xFF8E24AA, listOf("Live TV")),
        HomeSite("IPTV-Web", "https://iptv-web.app/", "Web-based IPTV channel viewer and streams", 0xFF2E7D32, listOf("Live TV")),
        HomeSite("PublicIPTV", "https://publiciptv.com/", "Free legal public IPTV channel directory", 0xFF455A64, listOf("Live TV")),
        HomeSite("FameLack", "https://famelack.com/", "Live television and broadcast streams", 0xFFD81B60, listOf("Live TV"))
    )

    /** Free Movies & TV Portals from Piradex. */
    val PIRADEX_MOVIES_AND_TV: List<HomeSite> = listOf(
        HomeSite("LookMovie", "https://www.lookmovie2.to/", "Free HD movies and series with multiple mirrors", 0xFF00ACC1, listOf("Free Movies & TV", "Movies")),
        HomeSite("FMovies", "https://fmoviess.org/", "Huge catalog of streaming movies and shows", 0xFFFFB300, listOf("Free Movies & TV", "Movies")),
        HomeSite("Soap2Day HD", "https://soap2dayhd.net/home/", "Popular movies, TV series and classics", 0xFF039BE5, listOf("Free Movies & TV", "Movies")),
        HomeSite("123Movies", "https://ww7.123moviesfree.net/home/", "Popular streaming films and TV show archives", 0xFFC62828, listOf("Free Movies & TV", "Movies")),
        HomeSite("Goojara", "https://ww1.goojara.to/", "Free movies, television series and anime", 0xFF8E24AA, listOf("Free Movies & TV", "Movies")),
        HomeSite("HydraHD", "https://hydrahd.gd/", "High definition movie and TV show streams", 0xFF00ACC1, listOf("Free Movies & TV", "Movies")),
        HomeSite("PopcornMovies", "https://popcornmovies.ac/", "Box office movies and cinema hits", 0xFFFDD835, listOf("Free Movies & TV", "Movies")),
        HomeSite("YesMovies", "https://ww.yesmovies.ag/", "Fast movie streaming and full TV seasons", 0xFFFF6F00, listOf("Free Movies & TV", "Movies")),
        HomeSite("PrimeWire", "https://www.primewire.mov/home", "Classic movies, trending series and video links", 0xFF283593, listOf("Free Movies & TV", "Movies")),
        HomeSite("AZMovies", "https://azmovies.to/", "High quality movie streams with server mirrors", 0xFF2E7D32, listOf("Free Movies & TV", "Movies")),
        HomeSite("HDToday", "https://hdtoday24.at/", "Free movies and TV series in HD quality", 0xFFFF8F00, listOf("Free Movies & TV", "Movies")),
        HomeSite("Cineby", "https://cinebytv.com/", "Free streaming movies, series and anime", 0xFF00B0FF, listOf("Free Movies & TV", "Movies")),
        HomeSite("RidoMovie", "https://ridomovie.to/rd1/", "Trending movies, IMDb rated films and series", 0xFF880E4F, listOf("Free Movies & TV", "Movies")),
        HomeSite("MovieBox", "https://officialmoviebox.com/", "Official movie box streaming and downloads", 0xFF1E88E5, listOf("Free Movies & TV", "Movies")),
        HomeSite("Ramoflix", "https://ramoflix.net/", "Free streaming movies and television series", 0xFFE53935, listOf("Free Movies & TV", "Movies")),
        HomeSite("Doraby", "https://doraby.com", "On-demand movies, shows and cinema releases", 0xFF3949AB, listOf("Free Movies & TV", "Movies")),
        HomeSite("Wmovies", "https://wmovies.org/", "Extensive movie library and series player", 0xFF00897B, listOf("Free Movies & TV", "Movies")),
        HomeSite("7Movies", "https://7movies.ac/", "Fast movie and television streaming portal", 0xFFD81B60, listOf("Free Movies & TV", "Movies")),
        HomeSite("Aether", "https://aether.ist/", "Curated high-quality cinema and episodic TV", 0xFF5E35B1, listOf("Free Movies & TV", "Movies")),
        HomeSite("Movy", "https://www.movy.sx/", "Streaming movies and trending TV shows", 0xFF43A047, listOf("Free Movies & TV", "Movies")),
        HomeSite("Flixer", "https://flixer.su/", "Full HD movies, recent releases and TV", 0xFFFB8C00, listOf("Free Movies & TV", "Movies")),
        HomeSite("Moviebite", "https://moviebite.org/", "Latest movie releases and binge-worthy shows", 0xFFE53935, listOf("Free Movies & TV", "Movies")),
        HomeSite("PrimeShows", "https://primeshows.org/", "Popular television series and full seasons", 0xFF1E88E5, listOf("Free Movies & TV", "TV Shows")),
        HomeSite("67Movies", "https://67movies.st/", "Free streaming films and television catalog", 0xFF546E7A, listOf("Free Movies & TV", "Movies")),
        HomeSite("ShuttleTV", "https://shuttletv.su/", "Movies and live entertainment streams", 0xFF7CB342, listOf("Free Movies & TV", "Movies")),
        HomeSite("RiveStream", "https://www.rivestream.app/", "Fast movie player and episodic series", 0xFF26A69A, listOf("Free Movies & TV", "Movies")),
        HomeSite("Stellar", "https://stellar.rip/", "Clean movie and series streaming interface", 0xFF3F51B5, listOf("Free Movies & TV", "Movies")),
        HomeSite("OnionPlay", "https://onionplay.ae/", "Popular movie streaming and series episodes", 0xFF6D4C41, listOf("Free Movies & TV", "Movies")),
        HomeSite("PressPlayz", "https://pressplayz.top/", "One-click streaming movies and TV shows", 0xFFE53935, listOf("Free Movies & TV", "Movies")),
        HomeSite("Nepu", "https://nepu.io/", "Movies, anime and drama series streaming", 0xFF8E24AA, listOf("Free Movies & TV", "Movies")),
        HomeSite("Mapple", "https://mapple.fun/", "Clean streaming portal for movies and shows", 0xFFF4511E, listOf("Free Movies & TV", "Movies")),
        HomeSite("StreamEx", "https://streamex.hn/", "Fast video streams and entertainment catalog", 0xFF5E35B1, listOf("Free Movies & TV", "Movies")),
        HomeSite("OpStream", "https://opstream.fun/", "On-demand movies, anime and TV seasons", 0xFF00897B, listOf("Free Movies & TV", "Movies")),
        HomeSite("LatestMovies", "https://latestmovies.net/", "New releases, cinema movies and series", 0xFFD81B60, listOf("Free Movies & TV", "Movies")),
        HomeSite("WillowMovies", "https://willowmovies.com/", "Free movies, series and documentaries", 0xFF33691E, listOf("Free Movies & TV", "Movies")),
        HomeSite("Vidplay", "https://vidplay.top/", "Fast-loading video player for movies and TV", 0xFF4527A0, listOf("Free Movies & TV", "Movies")),
        HomeSite("CinemaOS", "https://cinemaos.live/", "Modern cinema operating interface and movies", 0xFF00695C, listOf("Free Movies & TV", "Movies")),
        HomeSite("AnixTV", "https://anixtv.us.cc/", "Anime and animation series streaming", 0xFFAD1457, listOf("Free Movies & TV", "Anime")),
        HomeSite("Moviepire", "https://moviepire.ru/", "Curated movie portal and television shows", 0xFF37474F, listOf("Free Movies & TV", "Movies")),
        HomeSite("Flicker Mini", "https://flicker-mini.pages.dev/", "Minimalist ultra-fast movie streaming", 0xFF00838F, listOf("Free Movies & TV", "Movies")),
        HomeSite("M4UHD", "https://ww1.m4uhd.page/", "Full 1080p HD movies and series catalog", 0xFFB71C1C, listOf("Free Movies & TV", "Movies"))
    )


    /** Free Music Streaming — Pick a song. */
    val PICK_A_SONG: List<HomeSite> = listOf(
        HomeSite("Spotify Free - Web Player", "https://open.spotify.com/", "On-demand playlists, albums and podcasts", 0xFF2E7D32, listOf("Pick a song", "Music")),
        HomeSite("YouTube Music", "https://music.youtube.com/", "Songs, music videos, playlists and radio", 0xFFC62828, listOf("Pick a song", "Music")),
        HomeSite("SoundCloud", "https://soundcloud.com/", "Independent tracks, remixes and DJ sets", 0xFFE65100, listOf("Pick a song", "Music")),
        HomeSite("Audiomack", "https://audiomack.com/", "Hip-hop, Afrobeats and trending new music", 0xFFF9A825, listOf("Pick a song", "Music"))
    )

    /** Free Music Streaming — Free radio and stations. */
    val FREE_RADIO_AND_STATIONS: List<HomeSite> = listOf(
        HomeSite("Pandora - Personalized Radio", "https://www.pandora.com/", "Personalized artist and genre stations", 0xFF1565C0, listOf("Free radio and stations", "Radio", "Music")),
        HomeSite("iHeart - Live Radio", "https://www.iheart.com/", "Live AM/FM radio and artist stations", 0xFFC62828, listOf("Free radio and stations", "Radio")),
        HomeSite("AccuRadio - Curated Channels", "https://www.accuradio.com/", "Hundreds of curated music channels", 0xFFAD1457, listOf("Free radio and stations", "Radio")),
        HomeSite("SomaFM - Commercial-Free", "https://somafm.com/", "Listener-supported commercial-free radio", 0xFF5D4037, listOf("Free radio and stations", "Radio")),
        HomeSite("Jango - Personalized Radio", "https://www.jango.com/", "Free custom radio with no commercials", 0xFF00838F, listOf("Free radio and stations", "Radio", "Music")),
        HomeSite("TuneIn - Live Radio", "https://tunein.com/radio/home/", "Live local radio, news, sports and music", 0xFF00695C, listOf("Free radio and stations", "Radio")),
        HomeSite("Radio Garden - World Radio", "https://radio.garden/", "Spin the globe to tune in to live world radio", 0xFF2E7D32, listOf("Free radio and stations", "Radio"))
    )

    /** Free Music Streaming — Independent music and DJ mixes. */
    val INDEPENDENT_MUSIC_AND_MIXES: List<HomeSite> = listOf(
        HomeSite("Bandcamp - Independent Artists", "https://bandcamp.com/", "Stream and support independent artists", 0xFF0277BD, listOf("Independent & DJ mixes", "Music")),
        HomeSite("Jamendo - Free Independent", "https://www.jamendo.com/start?language=en", "Free independent music community", 0xFF558B2F, listOf("Independent & DJ mixes", "Music")),
        HomeSite("Mixcloud - DJ Mixes & Shows", "https://www.mixcloud.com/", "Long-form DJ mixes, radio shows and podcasts", 0xFF283593, listOf("Independent & DJ mixes", "Music"))
    )

    /** Streaming Sites — Free, licensed or public-domain options. */
    val FREE_LICENSED_STREAMING: List<HomeSite> = listOf(
        HomeSite("Tubi", "https://tubitv.com/", "Thousands of free movies and TV shows", 0xFFE65100, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Pluto TV", "https://pluto.tv/", "Hundreds of free live TV channels and on-demand", 0xFF283593, listOf("Free Movies & TV", "Live TV", "Movies & TV")),
        HomeSite("The Roku Channel", "https://therokuchannel.roku.com/", "Free hit movies, shows and 24/7 live TV", 0xFF6A1B9A, listOf("Free Movies & TV", "Movies & TV", "Live TV")),
        HomeSite("Plex - Free Movies & TV", "https://www.plex.tv/watch-free/", "Free on-demand movies, shows and live channels", 0xFFF9A825, listOf("Free Movies & TV", "Movies & TV", "Live TV")),
        HomeSite("Xumo Play", "https://play.xumo.com/", "Free live and on-demand TV and movies", 0xFF00838F, listOf("Free Movies & TV", "Live TV")),
        HomeSite("Crackle", "https://www.crackle.com/", "Free Hollywood movies, classics and series", 0xFFC62828, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Fawesome", "https://fawesome.tv/", "Free movies and TV shows with no sign-up", 0xFF2E7D32, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("FilmRise", "https://filmrise.com/", "Free movies, classic TV and true crime", 0xFF1565C0, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Filmzie", "https://filmzie.com/", "Free independent movies and hidden gems", 0xFFAD1457, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Kanopy", "https://www.kanopy.com/", "Acclaimed movies and documentaries (library card)", 0xFF37474F, listOf("Free Movies & TV", "Library card")),
        HomeSite("Hoopla", "https://www.hoopladigital.com/", "Movies, TV, music and audiobooks (library card)", 0xFF0277BD, listOf("Free Movies & TV", "Library card")),
        HomeSite("YouTube Movies", "https://www.youtube.com/movies", "Free-with-ads full-length movies on YouTube", 0xFFB71C1C, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Internet Archive - Movies", "https://archive.org/details/movies", "Moving Image Archive: public-domain films", 0xFF424242, listOf("Free Movies & TV", "Classics")),
        HomeSite("Bounce TV", "https://www.bouncetv.com/", "Free network series, movies and specials", 0xFF6A1B9A, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Midnight Pulp", "https://www.midnightpulp.com/", "Cult movies, horror, sci-fi and action", 0xFF4527A0, listOf("Free Movies & TV", "Classics")),
        HomeSite("Crunchyroll", "https://www.crunchyroll.com/", "Anime series, simulcasts and movies", 0xFFEF6C00, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Rakuten Viki", "https://www.viki.com/", "Asian dramas, movies and variety shows", 0xFF1E88E5, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Fandango at Home - Free", "https://www.fandangoathome.com/content/movies/free", "Free movies with ads on Fandango at Home", 0xFF1565C0, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("Prime Video - Free with Ads", "https://www.amazon.com/gp/video/storefront/?contentId=freetv", "Free-with-ads selection on Amazon Prime Video", 0xFF00838F, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("SBS On Demand", "https://www.sbs.com.au/ondemand/", "World movies, drama and documentaries", 0xFF283593, listOf("Free Movies & TV", "Movies & TV")),
        HomeSite("CONtv", "https://www.contv.com/", "Sci-fi, horror, anime, martial arts and comics", 0xFFC62828, listOf("Free Movies & TV", "Classics")),
        HomeSite("Veoh", "https://www.veoh.com/", "Independent videos, anime and classic shows", 0xFF00695C, listOf("Free Movies & TV", "Video"))
    )

    /** Streaming Sites — Streaming guides and aggregators. */
    val STREAMING_GUIDES: List<HomeSite> = listOf(
        HomeSite("Yidio", "https://www.yidio.com/", "Universal streaming guide across free and paid services", 0xFF1565C0, listOf("Streaming guides", "Movies & TV")),
        HomeSite("Movies Found Online", "https://moviesfoundonline.com/", "Curated public-domain and free movies across the web", 0xFF37474F, listOf("Streaming guides", "Classics")),
        HomeSite("Stremio", "https://www.stremio.com/", "Modern media center and streaming aggregator", 0xFF6A1B9A, listOf("Streaming guides", "Video"))
    )

    /** Streaming Sites — Paid or mixed services listed. */
    val PAID_OR_MIXED_SERVICES: List<HomeSite> = listOf(
        HomeSite("Netflix", "https://www.netflix.com/", "Movies, series and originals (subscription)", 0xFFB71C1C, listOf("Paid / Mixed", "Movies & TV")),
        HomeSite("Hulu", "https://www.hulu.com/", "TV shows, movies and live TV (subscription)", 0xFF2E7D32, listOf("Paid / Mixed", "Movies & TV")),
        HomeSite("Sling TV", "https://www.sling.com/", "Live TV channels and Freestream on-demand", 0xFF1E88E5, listOf("Paid / Mixed", "Live TV")),
        HomeSite("Peacock", "https://www.peacocktv.com/", "NBCUniversal movies, series and live sports", 0xFF283593, listOf("Paid / Mixed", "Movies & TV")),
        HomeSite("Prime Video", "https://www.amazon.com/gp/video/storefront/", "Amazon originals, movies, rentals and channels", 0xFF00838F, listOf("Paid / Mixed", "Movies & TV")),
        HomeSite("Disney+", "https://www.disneyplus.com/", "Disney, Pixar, Marvel, Star Wars and Nat Geo", 0xFF0D47A1, listOf("Paid / Mixed", "Movies & TV")),
        HomeSite("Max", "https://www.max.com/", "HBO, Warner Bros. movies and Max originals", 0xFF4527A0, listOf("Paid / Mixed", "Movies & TV"))
    )


    /** Additional built-in streaming & music sites. */
    val ADDITIONAL_BUILTINS: List<HomeSite> = listOf(
        HomeSite("Fandango at Home", "https://athome.fandango.com/", "Free movies with ads, plus rentals (formerly Vudu)", 0xFF1565C0, listOf("Movies & TV")),
        HomeSite("Plex Watch", "https://watch.plex.tv/", "Free movies, shows and live TV", 0xFFF9A825, listOf("Movies & TV", "Live TV")),
        HomeSite("Samsung TV Plus", "https://www.samsungtvplus.com/", "Free live channels", 0xFF0D47A1, listOf("Live TV")),
        HomeSite("Sling Freestream", "https://watch.sling.com/", "Free live channels and on-demand", 0xFF1E88E5, listOf("Live TV")),
        HomeSite("Shout! TV", "https://www.shoutfactorytv.com/", "Cult classics, cartoons and TV", 0xFFAD1457, listOf("Movies & TV", "Classics")),
        HomeSite("RetroCrush", "https://www.retrocrush.tv/", "Classic anime, free", 0xFFD84315, listOf("Classics")),
        HomeSite("YouTube", "https://www.youtube.com/", "Videos, music and live streams", 0xFFC62828, listOf("Video")),
        HomeSite("PBS", "https://www.pbs.org/video/", "Documentaries, drama and news", 0xFF1A237E, listOf("Movies & TV")),
        HomeSite("Twitch", "https://www.twitch.tv/", "Live streams", 0xFF6A1B9A, listOf("Video", "Live TV")),
        HomeSite("Dailymotion", "https://www.dailymotion.com/", "Videos and news", 0xFF0D47A1, listOf("Video")),
        HomeSite("Vimeo", "https://vimeo.com/", "Independent films and shorts", 0xFF00838F, listOf("Video")),
        HomeSite("Amazon Music", "https://music.amazon.com/", "Music (free tier)", 0xFF00838F, listOf("Music")),
        HomeSite("Deezer", "https://www.deezer.com/", "Music", 0xFF6A1B9A, listOf("Music")),
        HomeSite("Live365", "https://live365.com/", "Internet radio stations", 0xFF37474F, listOf("Radio")),
        HomeSite("Free Music Archive", "https://freemusicarchive.org/", "Free, legal downloads", 0xFF424242, listOf("Music"))
    )

    /**
     * Featured sections displayed at the top of the Stream browser picker before any other tiles,
     * matching the bookmark hierarchy.
     */
    val FEATURED_SECTIONS: List<Pair<String, List<HomeSite>>> = listOf(
        "Live Sports Streams" to PIRADEX_LIVE_SPORTS,
        "Live TV Channels" to PIRADEX_LIVE_TV,
        "Free Movies & TV Portals" to PIRADEX_MOVIES_AND_TV,
        "Streaming: Free, Licensed or Public-Domain Options" to FREE_LICENSED_STREAMING,
        "Free Music: Pick a Song" to PICK_A_SONG,
        "Free Music: Radio & Live Stations" to FREE_RADIO_AND_STATIONS,
        "Free Music: Independent Artists & DJ Mixes" to INDEPENDENT_MUSIC_AND_MIXES,
        "Streaming Guides & Aggregators" to STREAMING_GUIDES,
        "Paid or Mixed Streaming Services" to PAID_OR_MIXED_SERVICES
    )

    /**
     * All stream home page tiles in priority order: the user's bookmarked categories come first,
     * followed by the additional built-in sites.
     */
    val HOME: List<HomeSite> = (
        PIRADEX_LIVE_SPORTS +
            PIRADEX_LIVE_TV +
            PIRADEX_MOVIES_AND_TV +
            FREE_LICENSED_STREAMING +
            PICK_A_SONG +
            FREE_RADIO_AND_STATIONS +
            INDEPENDENT_MUSIC_AND_MIXES +
            STREAMING_GUIDES +
            PAID_OR_MIXED_SERVICES +
            ADDITIONAL_BUILTINS
        ).distinctBy { it.url }

    private val LISTEN = setOf("Music", "Radio", "Pick a song", "Free radio and stations", "Independent & DJ mixes")

    val video: List<StreamSite> = HOME.filter { s -> s.tags.none { it in LISTEN } }.map { StreamSite(it.name, it.url) }
    val audio: List<StreamSite> = HOME.filter { s -> s.tags.any { it in LISTEN } }.map { StreamSite(it.name, it.url) }

    /** Saved sites, one per line: label|url. */
    fun decode(text: String): List<StreamSite> = text.lines().mapNotNull { line ->
        val i = line.lastIndexOf('|')
        if (i <= 0) return@mapNotNull null
        val url = StreamUrls.normalize(line.substring(i + 1)) ?: return@mapNotNull null
        val lower = url.lowercase()
        if ("iprovpn.com" in lower || "privacysavvy.com" in lower) return@mapNotNull null
        StreamSite(line.substring(0, i).trim().ifEmpty { StreamUrls.titleOf(url) }, url)
    }.distinctBy { it.url }

    fun encode(sites: List<StreamSite>): String =
        sites.distinctBy { it.url }.joinToString("\n") { "${it.label.replace("|", "/").replace("\n", " ").take(40)}|${it.url}" }
}
