package com.aiconsole.kittlauncher2.ui.panes

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.media.LibraryItem
import com.aiconsole.kittlauncher2.media.LibraryStatus
import com.aiconsole.kittlauncher2.media.MediaBrowseRules
import com.aiconsole.kittlauncher2.media.MediaLibrary
import com.aiconsole.kittlauncher2.media.TapAction
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed

/**
 * Library tab of the Media page: pick a music app, search it, or browse its playlists, artists
 * and albums and start something without leaving the launcher. [nowPlayingPackage] puts the app
 * that's playing first in the list.
 */
@Composable
fun MediaLibraryPane(app: KittApplication, nowPlayingPackage: String?) {
    val library = app.mediaLibrary
    val sources by library.sources.collectAsState()
    val source by library.source.collectAsState()
    val path by library.path.collectAsState()
    val items by library.items.collectAsState()
    val status by library.status.collectAsState()

    LaunchedEffect(nowPlayingPackage) { library.refreshSources(nowPlayingPackage) }
    // Reconnect to the app used last time; let go of it when the tab is left.
    LaunchedEffect(sources) {
        if (library.source.value == null) {
            sources.firstOrNull { it.packageName == library.lastUsed }?.let(library::open)
        }
    }
    DisposableEffect(Unit) { onDispose { library.disconnect() } }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sources.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text(
                    "NO MUSIC APPS SHARE A LIBRARY",
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    color = DashGrey,
                    textAlign = TextAlign.Center
                )
                HelpText("Install and sign in to Spotify, YouTube Music or a USB music player, then come back.", Modifier.fillMaxWidth())
            }
            return@Column
        }
        LazyRow(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sources, key = { it.packageName }) { s ->
                KittButton(s.label, { library.open(s) }, Modifier.fillMaxHeight(), DashAmber, active = s.packageName == source?.packageName)
            }
        }
        val current = source
        if (current == null) {
            HelpText("Pick a music app above to browse its playlists and artists.", Modifier.fillMaxWidth())
            return@Column
        }
        SearchRow(library, enabled = status !is LibraryStatus.Connecting && status !is LibraryStatus.Failed)
        when (val st = status) {
            LibraryStatus.Idle, LibraryStatus.Connecting -> Note("CONNECTING TO ${current.label.uppercase()}…", DashGrey)
            is LibraryStatus.Failed -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(st.message, style = MaterialTheme.typography.bodyMedium, color = ScannerRed)
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittButton("Try again", { library.open(current) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
                    KittButton(
                        "Open ${current.label}",
                        {
                            app.packageManager.getLaunchIntentForPackage(current.packageName)?.let {
                                app.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        Modifier.weight(1f).fillMaxHeight(),
                        DashAmber
                    )
                }
            }
            LibraryStatus.Loading, LibraryStatus.Ready -> {
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KittButton("Back", { library.up() }, Modifier.width(96.dp).fillMaxHeight(), ScannerRed, enabled = path.canGoUp)
                    Text(
                        path.line().uppercase(),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = DashAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when {
                    st == LibraryStatus.Loading -> Note("LOADING…", DashGrey)
                    items.isEmpty() -> Note("NOTHING HERE", DashGrey)
                    else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items, key = { it.id }) { item -> ItemRow(library, item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(library: MediaLibrary, enabled: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    val go = { if (enabled) MediaBrowseRules.cleanQuery(query)?.let(library::search) }
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        KittTextField(query, { query = it }, "Search a song, artist or playlist and play it", Modifier.weight(1f), onDone = { go() })
        KittButton("Play", { go() }, Modifier.width(96.dp).fillMaxHeight(), LedGreen, enabled = enabled && MediaBrowseRules.cleanQuery(query) != null)
    }
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, Modifier.fillMaxWidth().padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, color = color, textAlign = TextAlign.Center)
}

@Composable
private fun ItemRow(library: MediaLibrary, item: LibraryItem) {
    val shape = MaterialTheme.shapes.small
    val cover by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.id) {
        value = library.cover(item)?.asImageBitmap()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittBezel, shape)
            .clickable {
                when (MediaBrowseRules.tapAction(item)) {
                    TapAction.Open -> library.openItem(item)
                    TapAction.Play -> library.play(item)
                }
            }
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.extraSmall).background(KittBezel), contentAlignment = Alignment.Center) {
            val bitmap = cover
            if (bitmap != null) {
                Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(if (item.browsable) "›" else "♪", style = MaterialTheme.typography.titleLarge, color = ScannerRed)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = DashGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // A playlist or album opens on tap; this plays the whole thing.
        if (item.browsable && item.playable) {
            KittButton("Play", { library.play(item) }, Modifier.width(88.dp).fillMaxHeight(), LedGreen)
        }
    }
}
