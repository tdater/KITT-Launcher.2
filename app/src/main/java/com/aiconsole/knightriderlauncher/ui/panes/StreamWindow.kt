package com.aiconsole.knightriderlauncher.ui.panes

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.stream.StreamSession
import com.aiconsole.knightriderlauncher.stream.StreamSite
import com.aiconsole.knightriderlauncher.stream.StreamSites
import com.aiconsole.knightriderlauncher.stream.StreamUrls
import com.aiconsole.knightriderlauncher.stream.Userscript
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import java.io.IOException
import kotlinx.coroutines.launch

private const val GREASY_FORK = "https://greasyfork.org/"

/** The streaming window drawn over the map pane. See [StreamSession]. */
@Composable
fun StreamWindow(session: StreamSession, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val scripts by app.userscripts.scripts.collectAsState()
    LaunchedEffect(scripts) { session.applyScripts(scripts) }
    val title = session.title

    KittPanel(
        title = title ?: "Stream",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton("Map", { session.visible = false }, Modifier.height(32.dp).width(64.dp), ScannerRed)
                KittButton("Close", { session.close() }, Modifier.height(32.dp).width(72.dp), DashGrey)
            }
        }
    ) {
        val pending = session.pendingInstall
        val fullscreen = session.fullscreenView
        when {
            pending != null -> InstallPrompt(app, session, pending)
            session.showScripts -> ScriptManager(app, session)
            title == null -> SitePicker(app, session)
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (fullscreen == null) Toolbar(app, session, scripts.count { it.enabled })
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { context -> session.webView(context).also { (it.parent as? ViewGroup)?.removeView(it) } },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (fullscreen != null) {
                        // The site's fullscreen video, kept inside the pane.
                        AndroidView(
                            factory = { fullscreen.also { (it.parent as? ViewGroup)?.removeView(it) } },
                            modifier = Modifier.fillMaxSize().background(KittBlack)
                        )
                        KittButton(
                            "Exit full",
                            { session.exitFullscreen() },
                            Modifier.align(Alignment.TopEnd).padding(6.dp).height(32.dp),
                            ScannerRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Toolbar(app: KittApplication, session: StreamSession, activeScripts: Int) {
    Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KittButton("Back", { session.goBack() }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        KittButton("Reload", { session.reload() }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        KittButton(
            if (session.desktop) "Desktop" else "Mobile",
            { session.setDesktopMode(!session.desktop) },
            Modifier.weight(1f).fillMaxHeight(),
            LedGreen,
            active = session.desktop
        )
        KittButton(
            "Scripts ($activeScripts)",
            { session.showScripts = true },
            Modifier.weight(1.2f).fillMaxHeight(),
            DashAmber,
            active = activeScripts > 0
        )
        KittButton(
            "Save",
            {
                session.currentPage()?.let { page ->
                    val saved = StreamSites.decode(app.settings.streamBookmarks.value)
                    app.settings.streamBookmarks.set(StreamSites.encode(saved + page))
                }
            },
            Modifier.weight(1f).fillMaxHeight(),
            LedGreen
        )
        KittButton("Sites", { session.pickSite() }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
    }
}

@Composable
private fun SitePicker(app: KittApplication, session: StreamSession) {
    var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val stored by app.settings.streamBookmarks.state.collectAsState()
    val mine = StreamSites.decode(stored)
    val target = StreamUrls.normalize(address)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KittTextField(address, { address = it }, "Any site, e.g. twitch.tv", Modifier.weight(1f), onDone = {
                    target?.let { session.open(it) }
                })
                KittButton(
                    label = "Go",
                    onClick = { target?.let { session.open(it) } },
                    modifier = Modifier.height(48.dp).width(72.dp),
                    color = ScannerRed,
                    active = target != null,
                    enabled = target != null
                )
            }
        }
        if (mine.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("My sites", Modifier.weight(1f), color = LedGreen)
                    KittButton(if (editing) "Done" else "Edit", { editing = !editing }, Modifier.height(32.dp).width(80.dp), DashAmber, active = editing)
                }
            }
            gridItems(mine, key = { "m_${it.url}" }) { site ->
                if (editing) {
                    KittButton("✕ ${site.label}", {
                        app.settings.streamBookmarks.set(StreamSites.encode(mine - site))
                    }, Modifier.height(56.dp), AlertRed)
                } else {
                    KittButton(site.label, { session.open(site.url, site.label) }, Modifier.height(56.dp), LedGreen, active = true)
                }
            }
        }
        siteSection("Watch free", StreamSites.video, session)
        siteSection("Listen", StreamSites.audio, session)
        item(span = { GridItemSpan(maxLineSpan) }) {
            HelpText(
                "Plays inside this window; links that would open a full-screen app are blocked. Tap SAVE on any page to add it " +
                    "to My sites. Paid services with copy protection (Netflix, Disney+…) may refuse to play in an embedded browser."
            )
        }
    }
}

private fun LazyGridScope.siteSection(title: String, sites: List<StreamSite>, session: StreamSession) {
    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(title, color = ScannerRed) }
    gridItems(sites, key = { "${title}_${it.url}" }) { site ->
        KittButton(site.label, { session.open(site.url, site.label) }, Modifier.height(56.dp), ScannerRed)
    }
}

@Composable
private fun ScriptManager(app: KittApplication, session: StreamSession) {
    val scope = rememberCoroutineScope()
    val scripts by app.userscripts.scripts.collectAsState()
    var address by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Userscripts", Modifier.weight(1f), color = DashAmber)
            KittButton("Done", { session.showScripts = false }, Modifier.height(36.dp).width(80.dp), LedGreen, active = true)
        }
        HelpText(
            "Greasemonkey/Tampermonkey-style scripts run on matching pages (Chrome extensions can't run in an embedded " +
                "browser). Browse Greasy Fork here and tap a script's Install button, or enter a .user.js address. " +
                "Changes apply after Reload. Only install scripts you trust: they can read the pages you're signed in to."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KittTextField(address, { address = it }, "https://…/script.user.js", Modifier.weight(1f))
            KittButton(
                "Install",
                { StreamUrls.normalize(address)?.let { session.pendingInstall = it } },
                Modifier.height(48.dp).width(88.dp),
                LedGreen,
                enabled = StreamUrls.normalize(address) != null
            )
            KittButton("Greasy Fork", { session.open(GREASY_FORK, "Greasy Fork") }, Modifier.height(48.dp), DashAmber)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (scripts.isEmpty()) item { HelpText("No scripts installed.") }
            items(scripts, key = { it.script.id }) { installed ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(KittBlack)
                        .border(1.dp, if (installed.enabled) LedGreen.copy(alpha = 0.6f) else KittBezel, MaterialTheme.shapes.small)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${installed.script.name} ${installed.script.version}".trim(),
                            style = MaterialTheme.typography.labelLarge,
                            color = DashWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            scopeText(installed.script),
                            style = MaterialTheme.typography.labelSmall,
                            color = DashGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    KittButton(
                        if (installed.enabled) "On" else "Off",
                        { scope.launch { app.userscripts.setEnabled(installed.script.id, !installed.enabled) } },
                        Modifier.height(40.dp).width(64.dp),
                        LedGreen,
                        active = installed.enabled
                    )
                    KittButton(
                        "Delete",
                        { scope.launch { app.userscripts.remove(installed.script.id) } },
                        Modifier.height(40.dp).width(80.dp),
                        AlertRed
                    )
                }
            }
        }
    }
}

/** Download, show what the script is and where it runs, then install on confirmation. */
@Composable
private fun InstallPrompt(app: KittApplication, session: StreamSession, url: String) {
    val scope = rememberCoroutineScope()
    var script by remember(url) { mutableStateOf<Userscript?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        try {
            script = app.userscripts.fetch(url)
        } catch (e: IOException) {
            error = e.message ?: "Download failed"
        } catch (e: IllegalArgumentException) {
            error = "Invalid address"
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Install userscript?", color = DashAmber)
        val loaded = script
        when {
            error != null -> Text("Couldn't load the script: $error", style = MaterialTheme.typography.bodyMedium, color = AlertRed)
            loaded == null -> HelpText("Downloading…")
            else -> {
                Text("${loaded.name} ${loaded.version}".trim(), style = MaterialTheme.typography.titleMedium, color = DashWhite)
                if (loaded.description.isNotEmpty()) HelpText(loaded.description)
                Text("Runs on: ${scopeText(loaded)}", style = MaterialTheme.typography.bodyMedium, color = DashAmber)
                HelpText("It can read and change those pages, including while you're signed in. Install only if you trust it.")
            }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Cancel", { session.pendingInstall = null }, Modifier.weight(1f).fillMaxHeight(), DashGrey)
            KittButton(
                "Install",
                {
                    loaded?.let {
                        scope.launch {
                            app.userscripts.install(it)
                            session.pendingInstall = null
                            session.reload()
                        }
                    }
                },
                Modifier.weight(1f).fillMaxHeight(),
                LedGreen,
                active = true,
                enabled = loaded != null
            )
        }
    }
}

private fun scopeText(script: Userscript): String =
    (script.matches + script.includes).ifEmpty { listOf("all sites") }.joinToString(", ")
