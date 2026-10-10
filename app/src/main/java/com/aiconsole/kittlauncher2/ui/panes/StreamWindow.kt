package com.aiconsole.kittlauncher2.ui.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.browser.BrowserSession
import com.aiconsole.kittlauncher2.stream.StreamSites
import com.aiconsole.kittlauncher2.ui.browser.BrowserView
import com.aiconsole.kittlauncher2.ui.browser.SiteSection
import com.aiconsole.kittlauncher2.ui.browser.SitePicker
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed

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
            bookmarks = app.settings.streamBookmarks,
            picker = {
                SitePicker(
                    session = it,
                    bookmarks = app.settings.streamBookmarks,
                    sections = listOf(SiteSection("Watch free", StreamSites.video), SiteSection("Listen", StreamSites.audio)),
                    help = "Import bookmarks from any browser's exported bookmarks file (HTML). Sign-ins stay in this window's own " +
                        "sandbox. Stuck behind a pop-up? Tap ✕ POPUPS. Page won't scroll? Use ▲ ▼ or switch Mobile/Desktop under MORE."
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
