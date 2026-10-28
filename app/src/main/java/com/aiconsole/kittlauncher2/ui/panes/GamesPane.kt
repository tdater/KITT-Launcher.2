package com.aiconsole.kittlauncher2.ui.panes

import com.aiconsole.kittlauncher2.ui.theme.LocalLiteMode
import com.aiconsole.kittlauncher2.stream.StreamSites
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import com.aiconsole.kittlauncher2.ui.components.CockpitSkin
import com.aiconsole.kittlauncher2.ui.components.SkinTab
import com.aiconsole.kittlauncher2.ui.components.ModeSwitch
import com.aiconsole.kittlauncher2.BuildConfig
import android.view.InputDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.browser.BrowserSession
import com.aiconsole.kittlauncher2.browser.GameSites
import com.aiconsole.kittlauncher2.ui.browser.BrowserView
import com.aiconsole.kittlauncher2.ui.browser.HomePrefs
import com.aiconsole.kittlauncher2.ui.browser.SiteHome
import com.aiconsole.kittlauncher2.ui.browser.TouchPad
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.delay

/**
 * Games page: RetroTV's retro-game browser, made for a head unit. The home page is a tile grid
 * of in-browser game sites (filters, favourites, "continue playing", my sites, emulator apps);
 * games play with a Bluetooth/USB controller or the on-screen touch pad, which reach the page as
 * a standard gamepad and/or keyboard keys (Auto picks per page). Game mode hides the launcher's
 * rail, dock and status strip; Focus game blows the game up to fill the page.
 */
@Composable
fun GamesPane(session: BrowserSession, modifier: Modifier = Modifier) {
    if (BuildConfig.HOLO_DESIGN) {
        GamesPaneHolo(session, modifier)
        return
    }
    val app = LocalContext.current.applicationContext as KittApplication
    val touchPad by app.settings.touchPad.state.collectAsState()
    var focusNote by remember { mutableStateOf<String?>(null) }
    val controllers by produceState(emptyList<String>()) {
        while (true) {
            value = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
                .filter { d -> d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                .map { it.name }
                .distinct()
            delay(3_000)
        }
    }
    val onGame = session.current?.isPicker == false
    val prefs = remember { HomePrefs(app.settings.gameBookmarks, app.settings.gameFavorites, app.settings.gameVisits) }

    KittPanel(
        title = if (session.immersive) (session.current?.title?.take(30) ?: "Game") else "Games",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (session.blockedCount > 0) Text("🛡 ${session.blockedCount}", style = MaterialTheme.typography.labelMedium, color = DashGrey)
                Text(
                    if (controllers.isEmpty()) "NO CONTROLLER" else "🎮 ${controllers.first().uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (controllers.isEmpty()) DashGrey else LedGreen,
                    maxLines = 1
                )
            }
        }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onGame) {
                LazyRow(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        KittButton(
                            if (session.immersive) "Exit game mode" else "Game mode",
                            { session.immersive = !session.immersive },
                            Modifier.height(40.dp),
                            ScannerRed,
                            active = session.immersive
                        )
                    }
                    item {
                        KittButton("Focus game", {
                            session.focusMedia { r -> focusNote = if (r == "none") "No game found on this page yet: start one first." else null }
                        }, Modifier.height(40.dp), DashAmber)
                    }
                    item { KittButton("Touch pad", { app.settings.touchPad.set(!touchPad) }, Modifier.height(40.dp), LedGreen, active = touchPad) }
                    item {
                        KittButton(
                            "Input: ${session.controllerMode.label}",
                            { session.changeControllerMode(session.controllerMode.next()) },
                            Modifier.height(40.dp),
                            LedGreen
                        )
                    }
                    if (session.immersive) {
                        item { KittButton("Back", { session.goBack() }, Modifier.height(40.dp).width(72.dp), DashAmber) }
                        item { KittButton("Games", { session.immersive = false; session.showPicker() }, Modifier.height(40.dp).width(88.dp), DashAmber) }
                    }
                }
                focusNote?.let { HelpText(it, color = DashAmber) }
            }
            BrowserView(
                session = session,
                bookmarks = app.settings.gameBookmarks,
                picker = {
                    SiteHome(
                        session = it,
                        sites = GameSites.ALL,
                        prefs = prefs,
                        continueTitle = "Continue playing",
                        searchHint = "Search games sites, or type any address",
                        help = "Pair a Bluetooth controller in Android settings or plug in a USB one, or use the on-screen TOUCH PAD. " +
                            "INPUT: AUTO sends a game controller to pages that read one and keyboard keys to the rest " +
                            "(A→X  B→Z  X→S  Y→A  L/R→Q/W  Start→Enter  Select→Shift). GAME MODE gives the game the whole screen; " +
                            "FOCUS GAME blows the game up inside the page. Ads and pop-ups are blocked; a held pop-up can be opened " +
                            "from the bar above the page. Downloads (emulator apps, homebrew) go to Downloads/KITT. " +
                            "These sites aren't run by KITT: what you play is up to you.",
                        sections = listOf("Emulator apps for the head unit" to GameSites.EMULATORS, "Free homebrew games" to GameSites.GAME_SOURCES)
                    )
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                compact = session.immersive,
                pageOverlay = { if (touchPad) TouchPad(session, Modifier.fillMaxSize()) }
            )
        }
    }
}

@Composable
private fun GamesPaneHolo(session: BrowserSession, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val touchPad by app.settings.touchPad.state.collectAsState()
    var focusNote by remember { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf(GameCategory.Arcade) }
    val controllers by produceState(emptyList<String>()) {
        while (true) {
            value = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
                .filter { d -> d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                .map { it.name }
                .distinct()
            delay(3_000)
        }
    }
    val onGame = session.current?.isPicker == false
    val prefs = remember { HomePrefs(app.settings.gameBookmarks, app.settings.gameFavorites, app.settings.gameVisits) }

    KittPanel(
        title = if (session.immersive) (session.current?.title?.take(30) ?: "Game") else "Games",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (session.blockedCount > 0) Text("🛡 ${session.blockedCount}", style = MaterialTheme.typography.labelMedium, color = DashGrey)
                GamepadBadge(controllers)
            }
        }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onGame) {
                LazyRow(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item { ModeSwitch("Game mode", session.immersive, { session.immersive = !session.immersive }) }
                    item {
                        ModeSwitch("Focus game", false, {
                            session.focusMedia { r -> focusNote = if (r == "none") "No game found on this page yet: start one first." else null }
                        })
                    }
                    item { ModeSwitch("Touch pad", touchPad, { app.settings.touchPad.set(!touchPad) }) }
                    item {
                        KittButton(
                            "Input: ${session.controllerMode.label}",
                            { session.changeControllerMode(session.controllerMode.next()) },
                            Modifier.height(40.dp),
                            LedGreen
                        )
                    }
                    if (session.immersive) {
                        item { KittButton("Back", { session.goBack() }, Modifier.height(40.dp).width(72.dp), DashAmber) }
                        item { KittButton("Games", { session.immersive = false; session.showPicker() }, Modifier.height(40.dp).width(88.dp), DashAmber) }
                    }
                }
                focusNote?.let { HelpText(it, color = DashAmber) }
            }
            val skin = CockpitSkin.current()
            val wide = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
            if (!onGame && !wide) {
                LazyRow(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    GameCategory.entries.forEach { c ->
                        item(key = c.name) { SkinTab(c.label(skin), category == c, { category = c }, Modifier.height(44.dp)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!onGame && wide) {
                    Column(Modifier.width(190.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        GameCategory.entries.forEach { c ->
                            SkinTab(c.label(skin), category == c, { category = c }, Modifier.fillMaxWidth().height(40.dp), arrow = true)
                        }
                    }
                }
            BrowserView(
                session = session,
                bookmarks = app.settings.gameBookmarks,
                picker = {
                  // Arcade keeps the full original catalog plus its emulator and homebrew sections,
                  // so no tile is lost; the other tabs focus one part of it.
                  key(category) {
                    SiteHome(
                        session = it,
                        sites = when (category) {
                            GameCategory.Arcade -> GameSites.ALL
                            GameCategory.Streaming -> StreamSites.HOME
                            // The driver's own saved sites are tiles too: an empty catalog leaves only those.
                            GameCategory.Saved -> emptyList<com.aiconsole.kittlauncher2.browser.HomeSite>()
                            GameCategory.Emulators -> GameSites.EMULATORS
                            GameCategory.Downloads -> GameSites.GAME_SOURCES
                        },
                        prefs = prefs,
                        continueTitle = "Continue playing",
                        searchHint = "Search games sites, or type any address",
                        help = "Pair a Bluetooth controller in Android settings or plug in a USB one, or use the on-screen TOUCH PAD. " +
                            "INPUT: AUTO sends a game controller to pages that read one and keyboard keys to the rest " +
                            "(A→X  B→Z  X→S  Y→A  L/R→Q/W  Start→Enter  Select→Shift). GAME MODE gives the game the whole screen; " +
                            "FOCUS GAME blows the game up inside the page. Ads and pop-ups are blocked; a held pop-up can be opened " +
                            "from the bar above the page. Downloads (emulator apps, homebrew) go to Downloads/KITT. " +
                            "These sites aren't run by KITT: what you play is up to you.",
                        sections = if (category == GameCategory.Arcade) {
                            listOf("Emulator apps for the head unit" to GameSites.EMULATORS, "Free homebrew games" to GameSites.GAME_SOURCES)
                        } else emptyList()
                    )
                  }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                compact = session.immersive,
                pageOverlay = { if (touchPad) TouchPad(session, Modifier.fillMaxSize()) }
            )
            }
        }
    }
}

/** The Games page's tabs. Arcade is the whole original catalog; the rest narrow it down. */
private enum class GameCategory {
    Arcade, Streaming, Saved, Emulators, Downloads;

    fun label(skin: CockpitSkin) = when (this) {
        Arcade -> skin.arcadeTab
        Streaming -> skin.streamTab
        Saved -> "Saved sites"
        Emulators -> "Emulators"
        Downloads -> "Downloads"
    }
}

/**
 * Top-bar controller status: a lamp that pulses while a gamepad is connected (still in lite
 * mode) with its name. Android doesn't say whether a pad is Bluetooth or USB, so it isn't guessed.
 */
@Composable
private fun GamepadBadge(controllers: List<String>) {
    if (controllers.isEmpty()) {
        Text("NO CONTROLLER", style = MaterialTheme.typography.labelMedium, color = DashGrey, maxLines = 1)
        return
    }
    val lite = LocalLiteMode.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val pulse = if (lite) null else rememberInfiniteTransition(label = "pad").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "padAlpha"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(LedGreen.copy(alpha = pulse?.value ?: 1f)) }
        Text(
            "GAMEPAD CONNECTED" + (padTransport(context, controllers.first())?.let { " ($it)" } ?: "") + if (controllers.size > 1) " +${controllers.size - 1}" else "",
            style = MaterialTheme.typography.labelMedium,
            color = LedGreen,
            maxLines = 1
        )
    }
}


/** BLUETOOTH if the controller is a paired Bluetooth device, USB otherwise; null when Android won't say (no permission). */
private fun padTransport(context: android.content.Context, name: String): String? = try {
    val adapter = (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
    when {
        adapter == null -> null
        adapter.bondedDevices.any { it.name == name } -> "BLUETOOTH"
        else -> "USB"
    }
} catch (_: SecurityException) {
    null
}
