package com.aiconsole.kittlauncher2.ui.panes

import com.aiconsole.kittlauncher2.BuildConfig
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.browser.BrowserSession
import com.aiconsole.kittlauncher2.media.NowPlaying
import com.aiconsole.kittlauncher2.nav.NavBridge
import com.aiconsole.kittlauncher2.stream.StreamSite
import com.aiconsole.kittlauncher2.stream.StreamSites
import com.aiconsole.kittlauncher2.ui.browser.BrowserView
import com.aiconsole.kittlauncher2.ui.browser.SitePicker
import com.aiconsole.kittlauncher2.ui.browser.SiteSection
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.components.VoiceModulator
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.delay
import java.util.Locale

/** Media page: artwork, track, progress and large transport buttons for the active session, plus Music Hub. */
@Composable
fun MediaPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val now by app.media.nowPlaying.collectAsState()
    val hasAccess by app.media.hasAccess.collectAsState()

    KittPanel(
        title = "Media",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            now?.let { Text(it.appLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = DashAmber) }
        }
    ) {
        var showMusicHub by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) { app.mediaLibrary.played.collect { showMusicHub = false } }
        LaunchedEffect(Unit) { app.mediaLibrary.tabRequests.collect { showMusicHub = true } }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(if (BuildConfig.HOLO_DESIGN) 32.dp else 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton("Now playing", { showMusicHub = false }, Modifier.weight(1f).fillMaxHeight(), ScannerRed, active = !showMusicHub)
                KittButton("Music Hub", { showMusicHub = true }, Modifier.weight(1f).fillMaxHeight(), ScannerRed, active = showMusicHub)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (showMusicHub) {
                    MediaMusicHubPane(app, onClose = { showMusicHub = false })
                } else {
                    val playing = now
                    if (BuildConfig.HOLO_DESIGN) {
                        MediaDeck(app, now, hasAccess, Modifier.fillMaxSize(), onLibrary = { showMusicHub = true })
                    } else when {
                        !hasAccess -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                            HelpText(
                                "Allow notification access so KITT can show what's playing in Spotify, YouTube Music, radio and other apps. " +
                                    "It's the same access the map uses for turn-by-turn."
                            )
                            KittButton(
                                label = "Allow notification access",
                                onClick = {
                                    try {
                                        context.startActivity(NavBridge.accessSettingsIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (_: ActivityNotFoundException) {
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                color = ScannerRed,
                                active = true
                            )
                        }
                        playing == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            VoiceModulator(active = false, modifier = Modifier.fillMaxWidth().height(80.dp))
                            Text(
                                "NOTHING PLAYING",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.titleMedium,
                                color = DashGrey,
                                textAlign = TextAlign.Center
                            )
                            HelpText("Start music in any app, or pick something from the Music Hub tab.", Modifier.fillMaxWidth())
                        }
                        else -> NowPlayingBody(app, playing)
                    }
                }
            }
        }
    }
}

/**
 * Music & Radio Hub: in-dash player and tuner for YouTube Music, Spotify Web, SoundCloud, Pandora,
 * live radio stations, and installed audio apps, with direct song/artist search.
 */
@Composable
fun MediaMusicHubPane(app: KittApplication, onClose: () -> Unit) {
    val context = LocalContext.current
    val musicBrowser = remember {
        BrowserSession(
            "music_hub",
            app.settings,
            app.userscripts,
            lite = { app.lite.value }
        )
    }
    DisposableEffect(musicBrowser) {
        onDispose { musicBrowser.close() }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchStatus by remember { mutableStateOf<String?>(null) }

    val installedSources by app.mediaLibrary.sources.collectAsState()
    LaunchedEffect(Unit) {
        app.mediaLibrary.refreshSources(null)
    }

    BackHandler {
        when {
            musicBrowser.fullscreenView != null -> musicBrowser.exitFullscreen()
            musicBrowser.canGoBack() -> musicBrowser.goBack()
            musicBrowser.immersive -> musicBrowser.immersive = false
            musicBrowser.current?.isPicker == false -> musicBrowser.showPicker()
            else -> onClose()
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Direct Search Bar Row
        Row(
            Modifier.fillMaxWidth().height(34.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KittTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search song, artist, album or station…",
                modifier = Modifier.weight(1f),
                onDone = {
                    if (searchQuery.isNotBlank()) {
                        val enc = Uri.encode(searchQuery.trim())
                        musicBrowser.open("https://music.youtube.com/search?q=$enc", "YT Music: ${searchQuery.trim()}")
                    }
                }
            )
            val hasQuery = searchQuery.isNotBlank()
            KittButton(
                "YT Music",
                {
                    if (hasQuery) {
                        val enc = Uri.encode(searchQuery.trim())
                        musicBrowser.open("https://music.youtube.com/search?q=$enc", "YT Music: ${searchQuery.trim()}")
                    }
                },
                Modifier.width(74.dp).fillMaxHeight(),
                ScannerRed,
                enabled = hasQuery
            )
            KittButton(
                "Spotify",
                {
                    if (hasQuery) {
                        val enc = Uri.encode(searchQuery.trim())
                        musicBrowser.open("https://open.spotify.com/search/$enc", "Spotify: ${searchQuery.trim()}")
                    }
                },
                Modifier.width(66.dp).fillMaxHeight(),
                LedGreen,
                enabled = hasQuery
            )
            KittButton(
                "YouTube",
                {
                    if (hasQuery) {
                        val enc = Uri.encode(searchQuery.trim())
                        musicBrowser.open("https://www.youtube.com/results?search_query=$enc", "YouTube: ${searchQuery.trim()}")
                    }
                },
                Modifier.width(70.dp).fillMaxHeight(),
                ScannerRed,
                enabled = hasQuery
            )
            KittButton(
                "SoundCloud",
                {
                    if (hasQuery) {
                        val enc = Uri.encode(searchQuery.trim())
                        musicBrowser.open("https://soundcloud.com/search?q=$enc", "SoundCloud: ${searchQuery.trim()}")
                    }
                },
                Modifier.width(82.dp).fillMaxHeight(),
                DashAmber,
                enabled = hasQuery
            )
            KittButton(
                "App Play",
                {
                    if (hasQuery) {
                        try {
                            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                                putExtra(android.app.SearchManager.QUERY, searchQuery.trim())
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            searchStatus = "No installed music app handled play intent."
                        }
                    }
                },
                Modifier.width(70.dp).fillMaxHeight(),
                DashAmber,
                enabled = hasQuery
            )
        }

        // Installed Media Apps Quick Bar
        if (installedSources.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text("INSTALLED APPS:", style = MaterialTheme.typography.labelSmall, color = DashGrey, modifier = Modifier.padding(end = 2.dp))
                }
                items(installedSources, key = { it.packageName }) { s ->
                    KittButton(
                        s.label,
                        {
                            try {
                                context.packageManager.getLaunchIntentForPackage(s.packageName)?.let {
                                    context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            } catch (_: Exception) {}
                        },
                        Modifier.fillMaxHeight(),
                        DashAmber
                    )
                }
            }
        }

        searchStatus?.let {
            HelpText(it, color = DashAmber)
        }

        // Embedded In-Dash Music & Radio Browser
        BrowserView(
            session = musicBrowser,
            bookmarks = app.settings.musicBookmarks,
            picker = { session ->
                MusicPicker(session, app)
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}

@Composable
private fun MusicPicker(session: BrowserSession, app: KittApplication) {
    SitePicker(
        session = session,
        bookmarks = app.settings.musicBookmarks,
        sections = listOf(
            SiteSection("Pick a Song (Free Web Players)", StreamSites.PICK_A_SONG.map { StreamSite(it.name, it.url) }),
            SiteSection("Radio & Live Stations", StreamSites.FREE_RADIO_AND_STATIONS.map { StreamSite(it.name, it.url) }),
            SiteSection("Independent & DJ Mixes", StreamSites.INDEPENDENT_MUSIC_AND_MIXES.map { StreamSite(it.name, it.url) }),
            SiteSection("Music Videos & Live Streams", listOf(
                StreamSite("YouTube Music Videos", "https://www.youtube.com/music"),
                StreamSite("Twitch Music", "https://www.twitch.tv/directory/category/music"),
                StreamSite("Dailymotion Music", "https://www.dailymotion.com/channel/music")
            ))
        ),
        help = "Music Hub plays free radio, live stations, podcasts and on-demand music directly on your dashboard. " +
            "Search any track above to play it on YouTube Music, Spotify or SoundCloud, or tap any station below. " +
            "Audio continues playing in the background when you switch screens."
    )
}

@Composable
private fun NowPlayingBody(app: KittApplication, now: NowPlaying) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val art = @Composable { artModifier: Modifier ->
            Box(
                artModifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(KittBlack)
                    .border(1.dp, KittBezel, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = now.art
                if (bitmap != null) {
                    Image(remember(bitmap) { bitmap.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    VoiceModulator(active = now.playing, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val details = @Composable { detailModifier: Modifier ->
            Column(detailModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    now.title ?: "Unknown track",
                    style = MaterialTheme.typography.headlineSmall,
                    color = DashWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                now.artist?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = DashAmber, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ProgressBar(now)
                Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittButton("Prev", { app.media.previous() }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                    KittButton(
                        if (now.playing) "Pause" else "Play",
                        { app.media.playPause() },
                        Modifier.weight(1.4f).fillMaxHeight(),
                        ScannerRed,
                        active = now.playing
                    )
                    KittButton("Next", { app.media.next() }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                }
                KittButton("Open ${now.appLabel}", { app.media.openApp() }, Modifier.fillMaxWidth().height(48.dp), DashAmber)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                art(Modifier.fillMaxHeight(0.9f))
                details(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                art(Modifier.weight(1f, fill = false).fillMaxWidth(0.7f))
                details(Modifier.fillMaxWidth())
            }
        }
    }
}

/** LED-segment progress bar with elapsed / total time. */
@Composable
fun ProgressBar(now: NowPlaying, modifier: Modifier = Modifier) {
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(now.playing) {
        while (now.playing) {
            clock = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val position = now.positionNow(clock)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val segments = 40
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (segments - 1)) / segments
            val lit = if (now.durationMs > 0) (segments * position / now.durationMs.toFloat()).toInt() else 0
            for (i in 0 until segments) {
                drawRect(
                    if (i < lit) ScannerRed else ScannerRed.copy(alpha = 0.15f),
                    Offset(i * (w + gap), 0f),
                    Size(w, size.height)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(clockText(position), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey)
            if (now.durationMs > 0) Text(clockText(now.durationMs), style = MaterialTheme.typography.labelSmall, color = DashGrey)
        }
    }
}

private fun clockText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(Locale.US, s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(Locale.US, s / 60, s % 60)
}
