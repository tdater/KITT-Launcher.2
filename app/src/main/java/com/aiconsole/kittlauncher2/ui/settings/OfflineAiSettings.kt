package com.aiconsole.kittlauncher2.ui.settings

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.assistant.VehicleContextFormatter
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

private const val TEST_QUESTION = "Is everything OK with the car right now?"

/** The offline AI (see OfflineBrain): download (345 MB), test, delete. */
@Composable
fun OfflineAiSettings(app: KittApplication) {
    val scope = rememberCoroutineScope()
    val brain = app.offlineBrain
    val installed by brain.installed.collectAsState()
    val progress by brain.download.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("OFFLINE AI", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        HelpText(
            "A small AI that runs on the head unit, for when there's no signal or no key: short answers about the car " +
                "from its live readings and codes. Free and private. It's much simpler than Gemini or Perplexity, takes a few " +
                "seconds per answer, and the first answer after a while takes longer while it loads."
        )
        val unsupported = brain.unsupported
        when {
            unsupported != null -> HelpText("Not available here: $unsupported.", color = DashAmber)
            progress != null -> Text("DOWNLOADING $progress%", style = MaterialTheme.typography.labelLarge, color = DashAmber)
            !installed -> KittButton("Download (345 MB)", {
                message = null
                scope.launch {
                    message = try {
                        brain.install()
                        "Offline AI ready. KITT uses it whenever the online AI can't answer."
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        "Download failed: ${e.message}"
                    }
                }
            }, Modifier.height(44.dp), DashAmber)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton(if (testing) "Thinking…" else "Test", {
                    testing = true
                    message = null
                    scope.launch {
                        val started = SystemClock.elapsedRealtime()
                        message = try {
                            val context = VehicleContextFormatter.vehicleContext(app.obd.vehicleContext(), "")
                            val reply = brain.answer(TEST_QUESTION, emptyList(), app.settings.persona.value, app.settings.driverName.value, context)
                            "\"$reply\" (${(SystemClock.elapsedRealtime() - started) / 100 / 10.0} s)"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: IOException) {
                            e.message
                        }
                        testing = false
                    }
                }, Modifier.height(44.dp).width(120.dp), LedGreen, enabled = !testing)
                KittButton("Delete", { scope.launch { brain.remove() } }, Modifier.height(44.dp).width(96.dp), DashGrey, enabled = !testing)
            }
        }
        brain.failed?.let { HelpText("It couldn't load on this head unit ($it).", color = DashAmber) }
        message?.let { HelpText(it, color = DashAmber) }
    }
}
