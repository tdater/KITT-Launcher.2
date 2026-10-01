package com.aiconsole.knightriderlauncher.ui.panes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.browser.Bookmarks
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.stream.StreamSite
import com.aiconsole.knightriderlauncher.stream.StreamSites
import com.aiconsole.knightriderlauncher.stream.StreamUrls
import com.aiconsole.knightriderlauncher.ui.browser.BrowserView
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The streaming window drawn over the map pane: the shared browser with a streaming-site picker. */
@Composable
fun StreamWindow(session: BrowserSession, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    KittPanel(
        title = session.current?.title?.take(40) ?: "Stream",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton("Map", { session.visible = false }, Modifier.height(32.dp).width(64.dp), ScannerRed)
                KittButton("Close", { session.close() }, Modifier.height(32.dp).width(72.dp), DashGrey)
            }
        }
    ) {
        BrowserView(
            session = session,
            picker = { StreamPicker(app, it) },
            modifier = Modifier.fillMaxSize(),
            extraTools = {
                KittButton("Save site", {
                    val tab = session.current
                    val url = tab?.url ?: return@KittButton
                    val saved = StreamSites.decode(app.settings.streamBookmarks.value)
                    app.settings.streamBookmarks.set(StreamSites.encode(saved + StreamSite(tab.title?.take(40) ?: StreamUrls.titleOf(url), url)))
                }, Modifier.fillMaxHeight(), LedGreen)
            }
        )
    }
}

@Composable
private fun StreamPicker(app: KittApplication, session: BrowserSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val stored by app.settings.streamBookmarks.state.collectAsState()
    val mine = StreamSites.decode(stored)
    val target = StreamUrls.normalize(address)

    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                val html = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw IOException("Couldn't open the file")
                }
                val found = Bookmarks.parse(html).map { (title, url) -> StreamSite(title.take(40), url) }
                app.settings.streamBookmarks.set(StreamSites.encode(StreamSites.decode(app.settings.streamBookmarks.value) + found))
                "Imported ${found.size} bookmarks."
            } catch (e: IOException) {
                "Import failed: ${e.message}"
            } catch (e: SecurityException) {
                "Import failed: ${e.message}"
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(Bookmarks.export(mine.map { s -> s.label to s.url }).toByteArray()) }
                        ?: throw IOException("Couldn't open the file")
                }
                "Exported ${mine.size} bookmarks."
            } catch (e: IOException) {
                "Export failed: ${e.message}"
            } catch (e: SecurityException) {
                "Export failed: ${e.message}"
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KittTextField(address, { address = it }, "Any site, e.g. twitch.tv", Modifier.weight(1f), onDone = { target?.let { session.open(it) } })
                KittButton("Go", { target?.let { session.open(it) } }, Modifier.height(48.dp).width(72.dp), ScannerRed, active = target != null, enabled = target != null)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader("My sites", Modifier.weight(1f), color = LedGreen)
                KittButton("Import bookmarks", { import.launch(arrayOf("text/html", "*/*")) }, Modifier.height(32.dp), DashAmber)
                KittButton("Export", { export.launch("kitt-bookmarks.html") }, Modifier.height(32.dp), DashAmber, enabled = mine.isNotEmpty())
                if (mine.isNotEmpty()) KittButton(if (editing) "Done" else "Edit", { editing = !editing }, Modifier.height(32.dp).width(72.dp), DashAmber, active = editing)
            }
        }
        message?.let { m -> item(span = { GridItemSpan(maxLineSpan) }) { HelpText(m, color = DashAmber) } }
        items(mine, key = { "m_${it.url}" }) { site ->
            if (editing) {
                KittButton("✕ ${site.label}", { app.settings.streamBookmarks.set(StreamSites.encode(mine - site)) }, Modifier.height(56.dp), AlertRed)
            } else {
                KittButton(site.label, { session.open(site.url, site.label) }, Modifier.height(56.dp), LedGreen, active = true)
            }
        }
        siteSection("Watch free", StreamSites.video, session)
        siteSection("Listen", StreamSites.audio, session)
        item(span = { GridItemSpan(maxLineSpan) }) {
            HelpText(
                "Import bookmarks from any browser's exported bookmarks file (HTML). Sign-ins stay in this window's own " +
                    "sandbox. Stuck behind a pop-up? Tap ✕ POPUPS. Page won't scroll? Use ▲ ▼ or switch Mobile/Desktop under MORE."
            )
        }
    }
}

private fun LazyGridScope.siteSection(title: String, sites: List<StreamSite>, session: BrowserSession) {
    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(title, color = ScannerRed) }
    items(sites, key = { "${title}_${it.url}" }) { site ->
        KittButton(site.label, { session.open(site.url, site.label) }, Modifier.height(56.dp), ScannerRed)
    }
}
