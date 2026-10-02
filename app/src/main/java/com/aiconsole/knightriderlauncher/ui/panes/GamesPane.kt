package com.aiconsole.knightriderlauncher.ui.panes

import android.view.InputDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.browser.GameSites
import com.aiconsole.knightriderlauncher.stream.StreamSite
import com.aiconsole.knightriderlauncher.ui.browser.BrowserView
import com.aiconsole.knightriderlauncher.ui.browser.SitePicker
import com.aiconsole.knightriderlauncher.ui.browser.SiteSection
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import kotlinx.coroutines.delay

/**
 * Games page: browser retro gaming with a Bluetooth or USB controller. Controller buttons are
 * turned into the keys web emulators use (or passed through for Gamepad API sites), and a
 * site's fullscreen fills the pane.
 */
@Composable
fun GamesPane(session: BrowserSession, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val keyboardMode by app.settings.gamepadKeyboard.state.collectAsState()
    val controllers by produceState(emptyList<String>()) {
        while (true) {
            value = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
                .filter { d -> d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                .map { it.name }
                .distinct()
            delay(3_000)
        }
    }

    KittPanel(
        title = "Games",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Text(
                if (controllers.isEmpty()) "NO CONTROLLER" else "🎮 ${controllers.first().uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                color = if (controllers.isEmpty()) DashGrey else LedGreen,
                maxLines = 1
            )
        }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                KittButton(
                    if (keyboardMode) "Controller → keys" else "Native gamepad",
                    { app.settings.gamepadKeyboard.set(!keyboardMode) },
                    Modifier.width(190.dp).height(40.dp),
                    LedGreen,
                    active = true
                )
                HelpText(
                    if (keyboardMode) "A→X  B→Z  X→S  Y→A  L/R→Q/W  Start→Enter  Select→Shift; D-pad and stick are arrows. Tap the game once so it has focus."
                    else "Buttons go straight to the page, for sites that support game controllers directly.",
                    Modifier.weight(1f)
                )
            }
            BrowserView(
                session = session,
                bookmarks = app.settings.gameBookmarks,
                picker = { GamePicker(app, it) },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun GamePicker(app: KittApplication, session: BrowserSession) {
    SitePicker(
        session = session,
        bookmarks = app.settings.gameBookmarks,
        sections = listOf(SiteSection("Play in the browser", GameSites.ALL.map { StreamSite(it.label, it.url) })),
        help = GameSites.ALL.joinToString("  ·  ") { "${it.label}: ${it.note}" } +
            ". Pair a Bluetooth controller in Android settings or plug in a USB dongle. Use a game's fullscreen button to fill " +
            "this pane; EXIT FULL returns."
    )
}
