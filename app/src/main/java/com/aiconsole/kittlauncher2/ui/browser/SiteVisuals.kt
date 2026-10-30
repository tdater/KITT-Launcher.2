package com.aiconsole.kittlauncher2.ui.browser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiconsole.kittlauncher2.stream.StreamSite
import com.aiconsole.kittlauncher2.stream.StreamUrls
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class BrandIcon {
    YouTube,
    YouTubeMusic,
    Spotify,
    Tubi,
    Twitch,
    SoundCloud,
    Pandora,
    PlutoTv,
    Plex,
    Roku,
    Crackle,
    Fawesome,
    IHeart,
    AccuRadio,
    SomaFM,
    RadioGarden,
    Bandcamp,
    Jamendo,
    Mixcloud,
    Gemini,
    Claude,
    ChatGPT,
    Perplexity,
    Stremio,
    ArchiveOrg,
    Yidio,
    MoviesFoundOnline,
    Netflix,
    Hulu,
    DisneyPlus,
    Max,
    Peacock,
    Crunchyroll,
    StreamEast,
    MethStreams,
    LiveTV,
    DaddyLive,
    CrackStreams,
    Sportsurge,
    LookMovie,
    FMovies,
    Soap2Day,
    OneTwoThreeMovies,
    Goojara,
    HydraHD,
    GlobeTV,
    TV247,
    OnHockey,
    PopcornMovies,
    PrimeWire,
    Score808,
    Generic
}

data class SiteBrand(
    val name: String,
    val domain: String,
    val primaryColor: Color,
    val accentColor: Color,
    val tag: String,
    val icon: BrandIcon,
    val monogram: String
)

object SiteVisuals {

    fun resolve(url: String, label: String = ""): SiteBrand {
        val lowerUrl = url.lowercase()
        val lowerLabel = label.lowercase()
        val domain = StreamUrls.titleOf(url).lowercase()

        return when {
            "music.youtube.com" in lowerUrl || "youtube music" in lowerLabel -> SiteBrand(
                name = "YouTube Music",
                domain = "music.youtube.com",
                primaryColor = Color(0xFFFF0000),
                accentColor = Color(0xFFFF5252),
                tag = "PICK A SONG",
                icon = BrandIcon.YouTubeMusic,
                monogram = "YM"
            )
            "youtube.com" in lowerUrl || "youtu.be" in lowerUrl || "youtube" in lowerLabel -> SiteBrand(
                name = if ("movie" in lowerUrl || "movie" in lowerLabel) "YouTube Movies" else "YouTube",
                domain = "youtube.com",
                primaryColor = Color(0xFFFF0000),
                accentColor = Color(0xFFFF8A80),
                tag = if ("movie" in lowerUrl || "movie" in lowerLabel) "FREE MOVIES" else "VIDEOS & MUSIC",
                icon = BrandIcon.YouTube,
                monogram = "YT"
            )
            "spotify.com" in lowerUrl || "spotify" in lowerLabel -> SiteBrand(
                name = "Spotify",
                domain = "open.spotify.com",
                primaryColor = Color(0xFF1DB954),
                accentColor = Color(0xFF57D68D),
                tag = "ON-DEMAND MUSIC",
                icon = BrandIcon.Spotify,
                monogram = "SP"
            )
            "tubitv.com" in lowerUrl || "tubi" in lowerLabel -> SiteBrand(
                name = "Tubi",
                domain = "tubitv.com",
                primaryColor = Color(0xFFFA4616),
                accentColor = Color(0xFFFF8A65),
                tag = "FREE MOVIES & TV",
                icon = BrandIcon.Tubi,
                monogram = "TU"
            )
            "twitch.tv" in lowerUrl || "twitch" in lowerLabel -> SiteBrand(
                name = "Twitch",
                domain = "twitch.tv",
                primaryColor = Color(0xFF9146FF),
                accentColor = Color(0xFFB388FF),
                tag = "LIVE STREAMS",
                icon = BrandIcon.Twitch,
                monogram = "TW"
            )
            "soundcloud.com" in lowerUrl || "soundcloud" in lowerLabel -> SiteBrand(
                name = "SoundCloud",
                domain = "soundcloud.com",
                primaryColor = Color(0xFFFF5500),
                accentColor = Color(0xFFFFAB40),
                tag = "TRACKS & REMIXES",
                icon = BrandIcon.SoundCloud,
                monogram = "SC"
            )
            "pandora.com" in lowerUrl || "pandora" in lowerLabel -> SiteBrand(
                name = "Pandora",
                domain = "pandora.com",
                primaryColor = Color(0xFF1565C0),
                accentColor = Color(0xFF64B5F6),
                tag = "CUSTOM RADIO",
                icon = BrandIcon.Pandora,
                monogram = "PA"
            )
            "pluto.tv" in lowerUrl || "pluto" in lowerLabel -> SiteBrand(
                name = "Pluto TV",
                domain = "pluto.tv",
                primaryColor = Color(0xFFFFD700),
                accentColor = Color(0xFFFFE082),
                tag = "100+ LIVE CHANNELS",
                icon = BrandIcon.PlutoTv,
                monogram = "PL"
            )
            "plex.tv" in lowerUrl || "plex" in lowerLabel -> SiteBrand(
                name = "Plex",
                domain = "plex.tv",
                primaryColor = Color(0xFFE5A00D),
                accentColor = Color(0xFFFFD54F),
                tag = "WATCH FREE",
                icon = BrandIcon.Plex,
                monogram = "PX"
            )
            "roku.com" in lowerUrl || "roku" in lowerLabel -> SiteBrand(
                name = "The Roku Channel",
                domain = "therokuchannel.roku.com",
                primaryColor = Color(0xFF6A1B9A),
                accentColor = Color(0xFFBA68C8),
                tag = "FREE HITS & LIVE TV",
                icon = BrandIcon.Roku,
                monogram = "RK"
            )
            "crackle.com" in lowerUrl || "crackle" in lowerLabel -> SiteBrand(
                name = "Crackle",
                domain = "crackle.com",
                primaryColor = Color(0xFFFF5722),
                accentColor = Color(0xFFFF8A65),
                tag = "HOLLYWOOD MOVIES",
                icon = BrandIcon.Crackle,
                monogram = "CR"
            )
            "fawesome.tv" in lowerUrl || "fawesome" in lowerLabel -> SiteBrand(
                name = "Fawesome",
                domain = "fawesome.tv",
                primaryColor = Color(0xFF2E7D32),
                accentColor = Color(0xFF81C784),
                tag = "FREE MOVIES",
                icon = BrandIcon.Fawesome,
                monogram = "FA"
            )
            "iheart.com" in lowerUrl || "iheart" in lowerLabel -> SiteBrand(
                name = "iHeartRadio",
                domain = "iheart.com",
                primaryColor = Color(0xFFC62828),
                accentColor = Color(0xFFEF9A9A),
                tag = "LIVE AM/FM RADIO",
                icon = BrandIcon.IHeart,
                monogram = "IH"
            )
            "accuradio.com" in lowerUrl || "accuradio" in lowerLabel -> SiteBrand(
                name = "AccuRadio",
                domain = "accuradio.com",
                primaryColor = Color(0xFFAD1457),
                accentColor = Color(0xFFF48FB1),
                tag = "CURATED CHANNELS",
                icon = BrandIcon.AccuRadio,
                monogram = "AR"
            )
            "somafm.com" in lowerUrl || "somafm" in lowerLabel -> SiteBrand(
                name = "SomaFM",
                domain = "somafm.com",
                primaryColor = Color(0xFFFF9800),
                accentColor = Color(0xFFFFCC80),
                tag = "COMMERCIAL-FREE",
                icon = BrandIcon.SomaFM,
                monogram = "SF"
            )
            "radio.garden" in lowerUrl || "radio garden" in lowerLabel -> SiteBrand(
                name = "Radio Garden",
                domain = "radio.garden",
                primaryColor = Color(0xFF00E676),
                accentColor = Color(0xFF69F0AE),
                tag = "WORLD RADIO GLOBE",
                icon = BrandIcon.RadioGarden,
                monogram = "RG"
            )
            "bandcamp.com" in lowerUrl || "bandcamp" in lowerLabel -> SiteBrand(
                name = "Bandcamp",
                domain = "bandcamp.com",
                primaryColor = Color(0xFF1DA0C3),
                accentColor = Color(0xFF80DEEA),
                tag = "INDIE ARTISTS",
                icon = BrandIcon.Bandcamp,
                monogram = "BC"
            )
            "jamendo.com" in lowerUrl || "jamendo" in lowerLabel -> SiteBrand(
                name = "Jamendo",
                domain = "jamendo.com",
                primaryColor = Color(0xFF7CB342),
                accentColor = Color(0xFFAED581),
                tag = "FREE INDIE MUSIC",
                icon = BrandIcon.Jamendo,
                monogram = "JM"
            )
            "mixcloud.com" in lowerUrl || "mixcloud" in lowerLabel -> SiteBrand(
                name = "Mixcloud",
                domain = "mixcloud.com",
                primaryColor = Color(0xFF283593),
                accentColor = Color(0xFF7986CB),
                tag = "DJ MIXES & SETS",
                icon = BrandIcon.Mixcloud,
                monogram = "MC"
            )
            "gemini.google.com" in lowerUrl || "gemini" in lowerLabel -> SiteBrand(
                name = "Gemini",
                domain = "gemini.google.com",
                primaryColor = Color(0xFF4285F4),
                accentColor = Color(0xFF8AB4F8),
                tag = "GOOGLE AI",
                icon = BrandIcon.Gemini,
                monogram = "GM"
            )
            "claude.ai" in lowerUrl || "claude" in lowerLabel -> SiteBrand(
                name = "Claude",
                domain = "claude.ai",
                primaryColor = Color(0xFFD97757),
                accentColor = Color(0xFFFFAB91),
                tag = "ANTHROPIC AI",
                icon = BrandIcon.Claude,
                monogram = "CL"
            )
            "chatgpt.com" in lowerUrl || "openai.com" in lowerUrl || "chatgpt" in lowerLabel -> SiteBrand(
                name = "ChatGPT",
                domain = "chatgpt.com",
                primaryColor = Color(0xFF10A37F),
                accentColor = Color(0xFF6EE7B7),
                tag = "OPENAI CHAT",
                icon = BrandIcon.ChatGPT,
                monogram = "GP"
            )
            "perplexity.ai" in lowerUrl || "perplexity" in lowerLabel -> SiteBrand(
                name = "Perplexity",
                domain = "perplexity.ai",
                primaryColor = Color(0xFF20B2AA),
                accentColor = Color(0xFF48D1CC),
                tag = "AI SEARCH & ENGINE",
                icon = BrandIcon.Perplexity,
                monogram = "PX"
            )
            "stremio.com" in lowerUrl || "stremio" in lowerLabel -> SiteBrand(
                name = "Stremio",
                domain = "stremio.com",
                primaryColor = Color(0xFF6A1B9A),
                accentColor = Color(0xFFCE93D8),
                tag = "MEDIA AGGREGATOR",
                icon = BrandIcon.Stremio,
                monogram = "ST"
            )
            "archive.org" in lowerUrl || "internet archive" in lowerLabel -> SiteBrand(
                name = "Internet Archive",
                domain = "archive.org",
                primaryColor = Color(0xFF607D8B),
                accentColor = Color(0xFFB0BEC5),
                tag = "PUBLIC DOMAIN",
                icon = BrandIcon.ArchiveOrg,
                monogram = "IA"
            )
            "yidio.com" in lowerUrl || "yidio" in lowerLabel -> SiteBrand(
                name = "Yidio",
                domain = "yidio.com",
                primaryColor = Color(0xFF0288D1),
                accentColor = Color(0xFF4FC3F7),
                tag = "STREAM GUIDE",
                icon = BrandIcon.Yidio,
                monogram = "YD"
            )
            "moviesfoundonline.com" in lowerUrl || "movies found online" in lowerLabel -> SiteBrand(
                name = "Movies Found Online",
                domain = "moviesfoundonline.com",
                primaryColor = Color(0xFFE65100),
                accentColor = Color(0xFFFFB74D),
                tag = "CURATED MOVIES",
                icon = BrandIcon.MoviesFoundOnline,
                monogram = "MO"
            )
            "netflix.com" in lowerUrl || "netflix" in lowerLabel -> SiteBrand(
                name = "Netflix",
                domain = "netflix.com",
                primaryColor = Color(0xFFE50914),
                accentColor = Color(0xFFFF5252),
                tag = "STREAMING",
                icon = BrandIcon.Netflix,
                monogram = "NF"
            )
            "hulu.com" in lowerUrl || "hulu" in lowerLabel -> SiteBrand(
                name = "Hulu",
                domain = "hulu.com",
                primaryColor = Color(0xFF1CE783),
                accentColor = Color(0xFF69F0AE),
                tag = "TV & MOVIES",
                icon = BrandIcon.Hulu,
                monogram = "HU"
            )
            "disneyplus.com" in lowerUrl || "disney" in lowerLabel -> SiteBrand(
                name = "Disney+",
                domain = "disneyplus.com",
                primaryColor = Color(0xFF0D47A1),
                accentColor = Color(0xFF42A5F5),
                tag = "DISNEY & MARVEL",
                icon = BrandIcon.DisneyPlus,
                monogram = "D+"
            )
            "max.com" in lowerUrl || "hbo" in lowerLabel || "max" in lowerLabel -> SiteBrand(
                name = "Max",
                domain = "max.com",
                primaryColor = Color(0xFF002BE7),
                accentColor = Color(0xFF448AFF),
                tag = "WARNER & HBO",
                icon = BrandIcon.Max,
                monogram = "MX"
            )
            "peacocktv.com" in lowerUrl || "peacock" in lowerLabel -> SiteBrand(
                name = "Peacock",
                domain = "peacocktv.com",
                primaryColor = Color(0xFF00897B),
                accentColor = Color(0xFF4DB6AC),
                tag = "NBCUNIVERSAL",
                icon = BrandIcon.Peacock,
                monogram = "PC"
            )
            "crunchyroll.com" in lowerUrl || "crunchyroll" in lowerLabel -> SiteBrand(
                name = "Crunchyroll",
                domain = "crunchyroll.com",
                primaryColor = Color(0xFFEF6C00),
                accentColor = Color(0xFFFFB74D),
                tag = "ANIME SERIES",
                icon = BrandIcon.Crunchyroll,
                monogram = "CR"
            )
            "streameast" in lowerUrl || "streameast" in lowerLabel -> SiteBrand(
                name = "StreamEast",
                domain = "streameast.ga",
                primaryColor = Color(0xFF00C853),
                accentColor = Color(0xFF69F0AE),
                tag = "LIVE SPORTS",
                icon = BrandIcon.StreamEast,
                monogram = "SE"
            )
            "methstreams" in lowerUrl || "methstreams" in lowerLabel -> SiteBrand(
                name = "MethStreams",
                domain = "methstreams.ms",
                primaryColor = Color(0xFF00E5FF),
                accentColor = Color(0xFF84FFFF),
                tag = "SPORTS PORTAL",
                icon = BrandIcon.MethStreams,
                monogram = "MS"
            )
            "livetv" in lowerUrl || "livetv" in lowerLabel -> SiteBrand(
                name = "LiveTV",
                domain = "livetv.sx",
                primaryColor = Color(0xFFFF3D00),
                accentColor = Color(0xFFFF8A65),
                tag = "LIVE SCORES & VIDEO",
                icon = BrandIcon.LiveTV,
                monogram = "LT"
            )
            "daddylive" in lowerUrl || "dlhd" in lowerUrl || "daddylive" in lowerLabel -> SiteBrand(
                name = "DaddyLive HD",
                domain = "dlhd.st",
                primaryColor = Color(0xFF9C27B0),
                accentColor = Color(0xFFE040FB),
                tag = "24/7 LIVE CHANNELS",
                icon = BrandIcon.DaddyLive,
                monogram = "DL"
            )
            "crackstreams" in lowerUrl || "crackstreams" in lowerLabel -> SiteBrand(
                name = "CrackStreams",
                domain = "crackstreams.io",
                primaryColor = Color(0xFFFFB300),
                accentColor = Color(0xFFFFE082),
                tag = "COMBAT & SPORTS",
                icon = BrandIcon.CrackStreams,
                monogram = "CS"
            )
            "sportsurge" in lowerUrl || "sportsurge" in lowerLabel -> SiteBrand(
                name = "Sportsurge",
                domain = "sportsurge.net",
                primaryColor = Color(0xFFFF1744),
                accentColor = Color(0xFFFF8A80),
                tag = "SPORTS AGGREGATOR",
                icon = BrandIcon.Sportsurge,
                monogram = "SS"
            )
            "lookmovie" in lowerUrl || "lookmovie" in lowerLabel -> SiteBrand(
                name = "LookMovie",
                domain = "lookmovie2.to",
                primaryColor = Color(0xFF00B0FF),
                accentColor = Color(0xFF80D8FF),
                tag = "FREE MOVIES & TV",
                icon = BrandIcon.LookMovie,
                monogram = "LM"
            )
            "fmovies" in lowerUrl || "fmovies" in lowerLabel -> SiteBrand(
                name = "FMovies",
                domain = "fmoviess.org",
                primaryColor = Color(0xFFFFAB00),
                accentColor = Color(0xFFFFD740),
                tag = "STREAMING MOVIES",
                icon = BrandIcon.FMovies,
                monogram = "FM"
            )
            "soap2day" in lowerUrl || "soap2day" in lowerLabel -> SiteBrand(
                name = "Soap2Day HD",
                domain = "soap2dayhd.net",
                primaryColor = Color(0xFF00B0FF),
                accentColor = Color(0xFF80D8FF),
                tag = "CINEMA & SERIES",
                icon = BrandIcon.Soap2Day,
                monogram = "S2"
            )
            "123movies" in lowerUrl || "123movies" in lowerLabel -> SiteBrand(
                name = "123Movies",
                domain = "123moviesfree.net",
                primaryColor = Color(0xFFD50000),
                accentColor = Color(0xFFFF8A80),
                tag = "BLOCKBUSTER MOVIES",
                icon = BrandIcon.OneTwoThreeMovies,
                monogram = "123"
            )
            "goojara" in lowerUrl || "goojara" in lowerLabel -> SiteBrand(
                name = "Goojara",
                domain = "goojara.to",
                primaryColor = Color(0xFFAA00FF),
                accentColor = Color(0xFFEA80FC),
                tag = "MOVIES & ANIME",
                icon = BrandIcon.Goojara,
                monogram = "GJ"
            )
            "hydrahd" in lowerUrl || "hydrahd" in lowerLabel -> SiteBrand(
                name = "HydraHD",
                domain = "hydrahd.gd",
                primaryColor = Color(0xFF00B4D8),
                accentColor = Color(0xFF90E0EF),
                tag = "FULL HD PORTAL",
                icon = BrandIcon.HydraHD,
                monogram = "HD"
            )
            "globetv" in lowerUrl || "globetv" in lowerLabel -> SiteBrand(
                name = "GlobeTV",
                domain = "globetv.app",
                primaryColor = Color(0xFF2979FF),
                accentColor = Color(0xFF82B1FF),
                tag = "WORLD LIVE TV",
                icon = BrandIcon.GlobeTV,
                monogram = "GT"
            )
            "tv247" in lowerUrl || "tv247" in lowerLabel -> SiteBrand(
                name = "TV247 US",
                domain = "tv247us.live",
                primaryColor = Color(0xFF0091EA),
                accentColor = Color(0xFF80D8FF),
                tag = "24/7 US CHANNELS",
                icon = BrandIcon.TV247,
                monogram = "24"
            )
            "onhockey" in lowerUrl || "onhockey" in lowerLabel -> SiteBrand(
                name = "OnHockey",
                domain = "onhockey.tv",
                primaryColor = Color(0xFF00E5FF),
                accentColor = Color(0xFFE0F7FA),
                tag = "ICE HOCKEY LIVE",
                icon = BrandIcon.OnHockey,
                monogram = "OH"
            )
            "popcorn" in lowerUrl || "popcorn" in lowerLabel -> SiteBrand(
                name = "PopcornMovies",
                domain = "popcornmovies.ac",
                primaryColor = Color(0xFFFFD600),
                accentColor = Color(0xFFFFF59D),
                tag = "CINEMA HITS",
                icon = BrandIcon.PopcornMovies,
                monogram = "PC"
            )
            "primewire" in lowerUrl || "primewire" in lowerLabel -> SiteBrand(
                name = "PrimeWire",
                domain = "primewire.mov",
                primaryColor = Color(0xFF3D5AFE),
                accentColor = Color(0xFF8C9EFF),
                tag = "ENTERTAINMENT INDEX",
                icon = BrandIcon.PrimeWire,
                monogram = "PW"
            )
            "score808" in lowerUrl || "score808" in lowerLabel -> SiteBrand(
                name = "Score808",
                domain = "score808hd.tv",
                primaryColor = Color(0xFF304FFE),
                accentColor = Color(0xFF8C9EFF),
                tag = "LIVE FOOTBALL & HD",
                icon = BrandIcon.Score808,
                monogram = "808"
            )
            else -> {
                val cleanLabel = label.ifEmpty { domain }
                val words = cleanLabel.split(" ", "-", ".").filter { it.isNotBlank() }
                val mono = (if (words.size > 1) words.take(2).joinToString("") { it.take(1) } else cleanLabel.take(2)).uppercase()
                val hash = abs(cleanLabel.hashCode())
                val palette = listOf(
                    Color(0xFF0288D1), Color(0xFF7B1FA2), Color(0xFF388E3C),
                    Color(0xFFF57C00), Color(0xFFD32F2F), Color(0xFF0097A7)
                )
                val c = palette[hash % palette.size]
                SiteBrand(
                    name = cleanLabel,
                    domain = domain,
                    primaryColor = c,
                    accentColor = c.copy(alpha = 0.85f),
                    tag = "WEBSITE",
                    icon = BrandIcon.Generic,
                    monogram = mono
                )
            }
        }
    }
}

/** Draws the authentic brand emblem using vector geometry. */
@Composable
fun BrandEmblem(brand: SiteBrand, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        when (brand.icon) {
            BrandIcon.YouTube, BrandIcon.YouTubeMusic -> {
                // Red rounded badge with white play triangle
                val pad = w * 0.12f
                drawRoundRect(
                    color = Color(0xFFFF0000),
                    topLeft = Offset(pad, pad * 1.15f),
                    size = Size(w - pad * 2, h - pad * 2.3f),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                val tri = Path().apply {
                    val r = w * 0.16f
                    moveTo(cx - r * 0.7f, cy - r)
                    lineTo(cx + r * 1.1f, cy)
                    lineTo(cx - r * 0.7f, cy + r)
                    close()
                }
                drawPath(tri, Color.White)
                if (brand.icon == BrandIcon.YouTubeMusic) {
                    drawCircle(Color.White.copy(alpha = 0.35f), w * 0.44f, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                }
            }
            BrandIcon.Spotify -> {
                // Spotify electric green disc + 3 acoustic wave arcs
                drawCircle(Color(0xFF1DB954), w * 0.44f, Offset(cx, cy))
                val waveColor = Color(0xFF121212)
                for (i in 0..2) {
                    val r = w * (0.17f + i * 0.11f)
                    val sw = 2.4.dp.toPx() - i * 0.3.dp.toPx()
                    drawArc(
                        color = waveColor,
                        startAngle = 200f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(cx - r + i * 1.2.dp.toPx(), cy - r + i * 1.8.dp.toPx()),
                        size = Size(r * 2, r * 2),
                        style = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
            }
            BrandIcon.Tubi -> {
                // Tubi TV capsule badge with scanlines & play marker
                drawRoundRect(
                    color = Color(0xFFFA4616),
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 0.64f),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                // Left & right TV antenna posts
                drawLine(Color(0xFFFA4616), Offset(cx - w * 0.15f, h * 0.18f), Offset(cx - w * 0.25f, h * 0.06f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFFFA4616), Offset(cx + w * 0.15f, h * 0.18f), Offset(cx + w * 0.25f, h * 0.06f), 2.dp.toPx(), StrokeCap.Round)
                // Inner dark screen
                drawRoundRect(
                    color = Color(0xFF111111),
                    topLeft = Offset(w * 0.20f, h * 0.26f),
                    size = Size(w * 0.60f, h * 0.48f),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                // White play triangle inside TV
                val tri = Path().apply {
                    val r = w * 0.12f
                    moveTo(cx - r * 0.6f, cy - r)
                    lineTo(cx + r, cy)
                    lineTo(cx - r * 0.6f, cy + r)
                    close()
                }
                drawPath(tri, Color(0xFFFA4616))
            }
            BrandIcon.Twitch -> {
                // Purple chat bubble with robot eye slits
                val path = Path().apply {
                    val l = w * 0.16f
                    val r = w * 0.84f
                    val t = h * 0.16f
                    val b = h * 0.74f
                    moveTo(l, t)
                    lineTo(r, t)
                    lineTo(r, b)
                    lineTo(cx + w * 0.12f, b)
                    lineTo(cx, h * 0.88f)
                    lineTo(cx, b)
                    lineTo(l, b)
                    close()
                }
                drawPath(path, Color(0xFF9146FF))
                // Robot slit eyes
                val eyeW = 3.dp.toPx()
                val eyeH = 8.dp.toPx()
                drawRect(Color.White, Offset(cx - 6.dp.toPx(), cy - 4.dp.toPx()), Size(eyeW, eyeH))
                drawRect(Color.White, Offset(cx + 3.dp.toPx(), cy - 4.dp.toPx()), Size(eyeW, eyeH))
            }
            BrandIcon.SoundCloud -> {
                // Cloud silhouette + equalizer bars
                drawCircle(Color(0xFFFF5500), w * 0.44f, Offset(cx, cy))
                val barW = 2.dp.toPx()
                val heights = listOf(6.dp, 10.dp, 15.dp, 11.dp, 7.dp)
                heights.forEachIndexed { i, bh ->
                    val bx = cx - 10.dp.toPx() + i * 5.dp.toPx()
                    val px = bh.toPx()
                    drawLine(Color.White, Offset(bx, cy + 6.dp.toPx()), Offset(bx, cy + 6.dp.toPx() - px), barW, StrokeCap.Round)
                }
            }
            BrandIcon.Pandora -> {
                // Pandora deep blue shield + bold geometric "P"
                drawRoundRect(
                    color = Color(0xFF1565C0),
                    topLeft = Offset(w * 0.12f, h * 0.12f),
                    size = Size(w * 0.76f, h * 0.76f),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                // Geometric white "P"
                drawLine(Color.White, Offset(cx - 5.dp.toPx(), cy - 9.dp.toPx()), Offset(cx - 5.dp.toPx(), cy + 9.dp.toPx()), 3.5.dp.toPx(), StrokeCap.Square)
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - 7.dp.toPx(), cy - 9.dp.toPx()),
                    size = Size(13.dp.toPx(), 11.dp.toPx()),
                    style = Stroke(3.5.dp.toPx())
                )
            }
            BrandIcon.RadioGarden -> {
                // Neon green 3D globe with latitude/longitude
                drawCircle(Color(0xFF00E676), w * 0.44f, Offset(cx, cy))
                val grid = Color(0xFF003300)
                drawCircle(grid, w * 0.44f, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                // Equator and meridian
                drawArc(color = grid, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(cx - w * 0.42f, cy - h * 0.18f), size = Size(w * 0.84f, h * 0.36f), style = Stroke(1.2.dp.toPx()))
                drawArc(color = grid, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(cx - w * 0.20f, cy - h * 0.42f), size = Size(w * 0.40f, h * 0.84f), style = Stroke(1.2.dp.toPx()))
                // Radio broadcast point
                drawCircle(Color.Red, 3.dp.toPx(), Offset(cx + 4.dp.toPx(), cy - 3.dp.toPx()))
            }
            BrandIcon.SomaFM -> {
                // Amber broadcast tower emitting waves
                drawCircle(Color(0xFF221100), w * 0.44f, Offset(cx, cy))
                drawCircle(Color(0xFFFF9800), w * 0.44f, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                // Pylon
                val pylon = Path().apply {
                    moveTo(cx, cy - 8.dp.toPx())
                    lineTo(cx - 6.dp.toPx(), cy + 10.dp.toPx())
                    moveTo(cx, cy - 8.dp.toPx())
                    lineTo(cx + 6.dp.toPx(), cy + 10.dp.toPx())
                    moveTo(cx - 4.dp.toPx(), cy + 2.dp.toPx())
                    lineTo(cx + 4.dp.toPx(), cy + 2.dp.toPx())
                }
                drawPath(pylon, Color(0xFFFF9800), style = Stroke(1.8.dp.toPx()))
                // Concentric waves
                drawArc(color = Color(0xFFFFCC80), startAngle = 220f, sweepAngle = 100f, useCenter = false, topLeft = Offset(cx - 9.dp.toPx(), cy - 14.dp.toPx()), size = Size(18.dp.toPx(), 12.dp.toPx()), style = Stroke(1.2.dp.toPx()))
            }
            BrandIcon.Bandcamp -> {
                // Cyan angled parallelogram
                drawCircle(Color(0xFF112229), w * 0.44f, Offset(cx, cy))
                val para = Path().apply {
                    moveTo(cx - 8.dp.toPx(), cy + 6.dp.toPx())
                    lineTo(cx - 2.dp.toPx(), cy - 6.dp.toPx())
                    lineTo(cx + 9.dp.toPx(), cy - 6.dp.toPx())
                    lineTo(cx + 3.dp.toPx(), cy + 6.dp.toPx())
                    close()
                }
                drawPath(para, Color(0xFF1DA0C3))
            }
            BrandIcon.PlutoTv -> {
                // Cosmic planet + planetary orbit ring
                drawCircle(Color(0xFF111122), w * 0.44f, Offset(cx, cy))
                drawArc(color = Color(0xFFFFD700), startAngle = 10f, sweepAngle = 340f, useCenter = false, topLeft = Offset(cx - w * 0.40f, cy - h * 0.16f), size = Size(w * 0.80f, h * 0.32f), style = Stroke(2.dp.toPx()))
                drawCircle(Color(0xFFFFD700), w * 0.22f, Offset(cx, cy))
            }
            BrandIcon.Plex -> {
                // Matte black shield + golden chevron
                drawRoundRect(Color(0xFF181818), Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), CornerRadius(6.dp.toPx()))
                val chev = Path().apply {
                    moveTo(cx - 4.dp.toPx(), cy - 8.dp.toPx())
                    lineTo(cx + 4.dp.toPx(), cy)
                    lineTo(cx - 4.dp.toPx(), cy + 8.dp.toPx())
                }
                drawPath(chev, Color(0xFFE5A00D), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
            }
            BrandIcon.Gemini -> {
                // Google AI sparkling diamond star
                drawCircle(Color(0xFF1A237E), w * 0.44f, Offset(cx, cy))
                val star = Path().apply {
                    val r = w * 0.34f
                    moveTo(cx, cy - r)
                    quadraticTo(cx, cy, cx + r, cy)
                    quadraticTo(cx, cy, cx, cy + r)
                    quadraticTo(cx, cy, cx - r, cy)
                    quadraticTo(cx, cy, cx, cy - r)
                    close()
                }
                drawPath(star, Color(0xFF8AB4F8))
            }
            BrandIcon.Claude -> {
                // Anthropic terracotta 8-arm sunburst asterism
                drawCircle(Color(0xFF33180F), w * 0.44f, Offset(cx, cy))
                for (a in 0 until 8) {
                    val rad = (a * 45f) * (PI / 180f).toFloat()
                    val r0 = w * 0.10f
                    val r1 = w * 0.34f
                    drawLine(
                        Color(0xFFFFAB91),
                        Offset(cx + r0 * cos(rad), cy + r0 * sin(rad)),
                        Offset(cx + r1 * cos(rad), cy + r1 * sin(rad)),
                        2.8.dp.toPx(),
                        StrokeCap.Round
                    )
                }
            }
            BrandIcon.ChatGPT -> {
                // OpenAI swirling knot
                drawCircle(Color(0xFF10A37F), w * 0.44f, Offset(cx, cy))
                for (a in 0 until 6) {
                    val rad = (a * 60f) * (PI / 180f).toFloat()
                    val r = w * 0.18f
                    drawCircle(
                        Color.White.copy(alpha = 0.85f),
                        r,
                        Offset(cx + (r * 0.65f) * cos(rad), cy + (r * 0.65f) * sin(rad)),
                        style = Stroke(1.4.dp.toPx())
                    )
                }
            }
            BrandIcon.Perplexity -> {
                // Teal infinity search compass
                drawCircle(Color(0xFF004D40), w * 0.44f, Offset(cx, cy))
                val r = w * 0.28f
                drawLine(Color(0xFF48D1CC), Offset(cx - r, cy), Offset(cx + r, cy), 2.dp.toPx())
                drawLine(Color(0xFF48D1CC), Offset(cx, cy - r), Offset(cx, cy + r), 2.dp.toPx())
                val d = r * 0.7f
                drawLine(Color(0xFF48D1CC), Offset(cx - d, cy - d), Offset(cx + d, cy + d), 1.5.dp.toPx())
                drawLine(Color(0xFF48D1CC), Offset(cx - d, cy + d), Offset(cx + d, cy - d), 1.5.dp.toPx())
            }
            BrandIcon.ArchiveOrg -> {
                // Parthenon temple facade
                drawCircle(Color(0xFF263238), w * 0.44f, Offset(cx, cy))
                // Pediment roof
                val roof = Path().apply {
                    moveTo(cx - 10.dp.toPx(), cy - 3.dp.toPx())
                    lineTo(cx, cy - 9.dp.toPx())
                    lineTo(cx + 10.dp.toPx(), cy - 3.dp.toPx())
                    close()
                }
                drawPath(roof, Color(0xFFB0BEC5))
                // Base
                drawLine(Color(0xFFB0BEC5), Offset(cx - 10.dp.toPx(), cy + 9.dp.toPx()), Offset(cx + 10.dp.toPx(), cy + 9.dp.toPx()), 2.dp.toPx())
                // 4 Columns
                for (c in 0..3) {
                    val colX = cx - 7.5.dp.toPx() + c * 5.dp.toPx()
                    drawLine(Color(0xFFB0BEC5), Offset(colX, cy - 2.dp.toPx()), Offset(colX, cy + 8.dp.toPx()), 1.8.dp.toPx())
                }
            }
            else -> {
                // Generic branded monogram badge with glow rim
                drawCircle(brand.primaryColor.copy(alpha = 0.20f), w * 0.44f, Offset(cx, cy))
                drawCircle(brand.primaryColor, w * 0.44f, Offset(cx, cy), style = Stroke(1.8.dp.toPx()))
            }
        }
    }
}

/**
 * High-tech cockpit site tile that makes each destination immediately visually recognizable
 * before navigating: vector brand badge, vibrant neon glow rim, category tag, and domain caption.
 */
@Composable
fun SiteCard(
    site: StreamSite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val brand = remember(site.url, site.label) { SiteVisuals.resolve(site.url, site.label) }
    val shape = MaterialTheme.shapes.small

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(KittBlack)
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        brand.primaryColor.copy(alpha = 0.95f),
                        brand.primaryColor.copy(alpha = 0.45f),
                        KittBezel
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick)
    ) {
        // Subtle left ambient brand color wash
        Box(
            Modifier
                .fillMaxHeight()
                .width(48.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(brand.primaryColor.copy(alpha = 0.24f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Visual brand emblem
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                BrandEmblem(brand, Modifier.fillMaxSize())
                if (brand.icon == BrandIcon.Generic) {
                    Text(
                        brand.monogram,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = DashWhite
                    )
                }
            }

            // Name, tag & domain
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = site.label.ifEmpty { brand.name },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = DashWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Category pill
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(brand.primaryColor.copy(alpha = 0.22f))
                            .border(0.8.dp, brand.primaryColor.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = brand.tag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = brand.accentColor,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = brand.domain,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = DashGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Optional delete button when in edit mode
            if (onDelete != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(AlertRed.copy(alpha = 0.2f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
