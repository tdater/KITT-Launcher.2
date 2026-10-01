package com.aiconsole.knightriderlauncher.ui.panes

import android.view.InputDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.browser.GameSites
import com.aiconsole.knightriderlauncher.stream.StreamUrls
import com.aiconsole.knightriderlauncher.ui.browser.BrowserView
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
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
            BrowserView(session = session, picker = { GamePicker(it) }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun GamePicker(session: BrowserSession) {
    var address by remember { mutableStateOf("") }
    val target = StreamUrls.normalize(address)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(200.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KittTextField(address, { address = it }, "Another game site…", Modifier.weight(1f), onDone = { target?.let { session.open(it) } })
                KittButton("Go", { target?.let { session.open(it) } }, Modifier.height(48.dp).width(72.dp), ScannerRed, enabled = target != null)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Play in the browser", color = ScannerRed) }
        items(GameSites.ALL, key = { it.url }) { site ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KittButton(site.label, { session.open(site.url, site.label) }, Modifier.fillMaxWidth().height(56.dp), ScannerRed, active = true)
                Text(site.note, style = MaterialTheme.typography.labelSmall, color = DashAmber, maxLines = 2)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            HelpText(
                "Pair a Bluetooth controller in Android settings or plug in a USB dongle. EmulatorJS plays ROM files you own " +
                    "from USB or storage. Use a game's fullscreen button to fill this pane; EXIT FULL returns."
            )
        }
    }
}
