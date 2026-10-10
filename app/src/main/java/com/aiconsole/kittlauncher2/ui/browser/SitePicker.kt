package com.aiconsole.kittlauncher2.ui.browser

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import com.aiconsole.kittlauncher2.browser.Bookmarks
import com.aiconsole.kittlauncher2.browser.BrowserSession
import com.aiconsole.kittlauncher2.data.settings.KittSettings
import com.aiconsole.kittlauncher2.stream.StreamSite
import com.aiconsole.kittlauncher2.stream.StreamSites
import com.aiconsole.kittlauncher2.stream.StreamUrls
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.components.SectionHeader
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** A titled group of one-tap sites on a new-tab page. */
data class SiteSection(val title: String, val sites: List<StreamSite>)

/**
 * The new-tab page every browser uses: an address bar that can go to or save any address, the
 * driver's own saved sites (with bookmark import/export), then this page's suggested sites.
 */
@Composable
fun SitePicker(
    session: BrowserSession,
    bookmarks: KittSettings.Pref<String>,
    sections: List<SiteSection>,
    help: String,
    extra: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val stored by bookmarks.state.collectAsState()
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
                bookmarks.set(StreamSites.encode(StreamSites.decode(bookmarks.value) + found))
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
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KittTextField(address, { address = it }, "Type any address, e.g. twitch.tv", Modifier.weight(1f), onDone = { target?.let { session.open(it) } })
                KittButton("Go", { target?.let { session.open(it) } }, Modifier.height(48.dp).width(64.dp), ScannerRed, active = target != null, enabled = target != null)
                KittButton(
                    "★ Save",
                    {
                        target?.let {
                            bookmarks.set(StreamSites.encode(StreamSites.decode(bookmarks.value) + StreamSite(StreamUrls.titleOf(it), it)))
                            message = "Saved ${StreamUrls.titleOf(it)} to My sites."
                            address = ""
                        }
                    },
                    Modifier.height(48.dp).width(96.dp),
                    LedGreen,
                    enabled = target != null
                )
            }
        }
        extra?.let { content -> item(span = { GridItemSpan(maxLineSpan) }) { content() } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader("My sites", Modifier.weight(1f), color = LedGreen)
                KittButton("Import bookmarks", { import.launch(arrayOf("text/html", "*/*")) }, Modifier.height(32.dp), DashAmber)
                KittButton("Export", { export.launch("kitt-bookmarks.html") }, Modifier.height(32.dp), DashAmber, enabled = mine.isNotEmpty())
                if (mine.isNotEmpty()) KittButton(if (editing) "Done" else "Edit", { editing = !editing }, Modifier.height(32.dp).width(72.dp), DashAmber, active = editing)
            }
        }
        message?.let { m -> item(span = { GridItemSpan(maxLineSpan) }) { HelpText(m, color = DashAmber) } }
        if (mine.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HelpText("Save any site: type its address above and tap ★ SAVE, or tap ★ SAVE in the toolbar while you're on it.")
            }
        }
        items(mine, key = { "m_${it.url}" }) { site ->
            if (editing) {
                KittButton("✕ ${site.label}", { bookmarks.set(StreamSites.encode(mine - site)) }, Modifier.height(56.dp), AlertRed)
            } else {
                KittButton(site.label, { session.open(site.url, site.label) }, Modifier.height(56.dp), LedGreen, active = true)
            }
        }
        sections.forEach { section ->
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(section.title, color = ScannerRed) }
            items(section.sites, key = { "${section.title}_${it.url}" }) { site ->
                KittButton(site.label, { session.open(site.url, site.label) }, Modifier.height(56.dp), ScannerRed)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { HelpText(help) }
    }
}
