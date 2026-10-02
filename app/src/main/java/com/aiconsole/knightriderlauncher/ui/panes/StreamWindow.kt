package com.aiconsole.knightriderlauncher.ui.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.stream.StreamSites
import com.aiconsole.knightriderlauncher.ui.browser.BrowserView
import com.aiconsole.knightriderlauncher.ui.browser.SiteSection
import com.aiconsole.knightriderlauncher.ui.browser.SitePicker
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

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
