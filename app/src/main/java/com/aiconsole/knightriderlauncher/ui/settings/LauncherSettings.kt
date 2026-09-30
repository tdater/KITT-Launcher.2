package com.aiconsole.knightriderlauncher.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.MainActivity
import com.aiconsole.knightriderlauncher.data.HardwareKeys
import com.aiconsole.knightriderlauncher.data.InstalledApps
import com.aiconsole.knightriderlauncher.data.KeyAction
import com.aiconsole.knightriderlauncher.data.KeyBindings
import com.aiconsole.knightriderlauncher.data.LauncherApp
import com.aiconsole.knightriderlauncher.data.LauncherBackup
import com.aiconsole.knightriderlauncher.data.ProblemReport
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittToggle
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import java.io.IOException
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SCREENSAVER_OPTIONS = listOf(0, 2, 5, 10, 20)

/** Home screen extras: status strip, themed icons, screensaver and the start-up routine. */
@Composable
fun HomeScreenSettings(app: KittApplication) {
    val settings = app.settings
    val context = LocalContext.current
    val strip by settings.statusStrip.state.collectAsState()
    val themedIcons by settings.themedIcons.state.collectAsState()
    val sounds by settings.buttonSounds.state.collectAsState()
    val weatherAlerts by settings.weatherAlerts.state.collectAsState()
    val saver by settings.screensaverMinutes.state.collectAsState()
    val startupApp by settings.startupApp.state.collectAsState()
    val resume by settings.startupResumeMedia.state.collectAsState()
    val apps by produceState(emptyList<LauncherApp>()) { value = InstalledApps.load(context) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Home screen")
        KittToggle("Status strip", strip, { settings.statusStrip.set(it) }, description = "Clock, heading, Bluetooth, Wi-Fi, GPS, OBD and battery voltage under the scanner.")
        KittToggle("Themed app icons", themedIcons, { settings.themedIcons.set(it) }, description = "Recolour every app icon in the theme's accent.")
        KittToggle("Button sounds", sounds, { settings.buttonSounds.set(it) }, description = "A short console blip on every KITT button press.")
        KittToggle(
            "Weather warnings",
            weatherAlerts,
            { settings.weatherAlerts.set(it) },
            description = "KITT warns about ice, freezing rain, fog, storms and strong wind (Open-Meteo, no account needed). Needs location."
        )
        Text("SCREENSAVER WHEN PARKED", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        ChoiceRow(SCREENSAVER_OPTIONS, saver, { if (it == 0) "Off" else "$it min" }, { settings.screensaverMinutes.set(it) })
        Text("ON START OR WAKE", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        ChoiceRow(
            options = listOf("") + apps.map { it.key },
            selected = startupApp,
            label = { key -> if (key.isEmpty()) "Open nothing" else apps.firstOrNull { it.key == key }?.label ?: key },
            onSelect = { settings.startupApp.set(it) }
        )
        KittToggle("Resume audio", resume, { settings.startupResumeMedia.set(it) }, description = "Press play on whatever was last playing.")
        HelpText("Runs when the launcher starts and when the screen wakes after 5+ minutes off (ignition on).")
    }
}

/** Map steering-wheel or other hardware buttons to launcher actions. */
@Composable
fun ButtonSettings(app: KittApplication) {
    val settings = app.settings
    val stored by settings.keyBindings.state.collectAsState()
    val bindings = KeyBindings.decode(stored)
    val learning by HardwareKeys.learning.collectAsState()
    val learned by HardwareKeys.learned.collectAsState()
    var assigning by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(learned) { learned?.let { assigning = it } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Steering-wheel buttons")
        bindings.forEach { (code, action) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")} → ${action.label}", Modifier.weight(1f), color = DashWhite)
                KittButton("Remove", { settings.keyBindings.set(KeyBindings.encode(bindings - code)) }, Modifier.height(40.dp).width(96.dp), AlertRed)
            }
        }
        val pending = assigning
        when {
            learning -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PRESS THE BUTTON NOW…", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = ScannerRed)
                KittButton("Cancel", { HardwareKeys.cancelLearning() }, Modifier.height(40.dp).width(96.dp), DashGrey)
            }
            pending != null -> {
                Text("${android.view.KeyEvent.keyCodeToString(pending).removePrefix("KEYCODE_")}: CHOOSE AN ACTION", style = MaterialTheme.typography.labelLarge, color = DashAmber)
                ChoiceRow(KeyAction.entries, null, { it.label }, { action ->
                    settings.keyBindings.set(KeyBindings.encode(bindings + (pending to action)))
                    assigning = null
                }, color = LedGreen)
            }
            else -> KittButton("Learn a button", { HardwareKeys.startLearning() }, Modifier.fillMaxWidth().height(44.dp), LedGreen, active = true)
        }
        HelpText(
            "Works while the launcher is in front, for buttons the head unit sends as normal key presses. " +
                "Media keys often go straight to the playing app instead."
        )
    }
}

/** "Hey KITT": asks for the microphone first if needed. */
@Composable
fun WakeWordToggle(app: KittApplication) {
    val context = LocalContext.current
    val on by app.settings.wakeWord.state.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.settings.wakeWord.set(true)
    }
    KittToggle(
        "\"Hey KITT\" wake word",
        on,
        { enable ->
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (enable && !granted) permission.launch(Manifest.permission.RECORD_AUDIO) else app.settings.wakeWord.set(enable)
        },
        description = "Listens while the home screen is showing. Uses the speech recognizer below; some chime " +
            "each time they restart, and a network recognizer uses data. Say \"Hey KITT\", then your question or command."
    )
}

/** Save or restore settings, gauges, custom PIDs and userscripts. */
@Composable
fun BackupSettings(app: KittApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val backup = remember { LauncherBackup(context) }
    val report = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { ProblemReport.write(context, app.obd, it) }
                        ?: throw IOException("Couldn't open the file")
                }
                "Report saved. Send me the file."
            } catch (e: IOException) {
                "Report failed: ${e.message}"
            } catch (e: SecurityException) {
                "Report failed: ${e.message}"
            }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use(backup::export) ?: throw IOException("Couldn't open the file")
                }
                "Backup saved."
            } catch (e: IOException) {
                "Backup failed: ${e.message}"
            } catch (e: SecurityException) {
                "Backup failed: ${e.message}"
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use(backup::import) ?: throw IOException("Couldn't open the file")
                }
                // Settings are read once at start: relaunch so everything picks up the restored values.
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                exitProcess(0)
            } catch (e: IOException) {
                message = "Restore failed: ${e.message}"
            } catch (e: SecurityException) {
                message = "Restore failed: ${e.message}"
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Backup")
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Save backup", { export.launch("kitt-launcher-backup.json") }, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = true)
            KittButton("Restore…", { import.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        }
        KittButton(
            "Report a problem",
            { report.launch("kitt-report-${System.currentTimeMillis() / 1000}.txt") },
            Modifier.fillMaxWidth().height(48.dp),
            ScannerRed
        )
        HelpText(
            message ?: ("Report a problem saves a text file with recent crashes, the OBD connection log, settings (no API keys) " +
                "and this app's log. " +
                "Save backup saves settings, gauges, custom PIDs and userscripts to a file (USB stick, Downloads…). " +
                "API keys aren't included; re-enter them after restoring. Restoring restarts the launcher.")
        )
    }
}
