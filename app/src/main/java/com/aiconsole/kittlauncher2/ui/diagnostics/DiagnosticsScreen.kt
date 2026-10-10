package com.aiconsole.kittlauncher2.ui.diagnostics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.ObdState
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import java.util.Locale

enum class DiagTab(val label: String) {
    Gauges("Gauges"),
    Data("Data"),
    Codes("Codes"),
    Vehicle("Vehicle"),
    Tools("Tools"),
    Pids("PIDs"),
    Link("Link")
}

/** Car Scanner-style diagnostics in KITT dress: gauges, fault codes, PIDs and the adapter link. */
@Composable
fun DiagnosticsScreen(
    onDismiss: () -> Unit,
    onAskKitt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as KittApplication
    val state by app.obd.state.collectAsState()
    val hz by app.obd.refreshHz.collectAsState()
    val initial = if (app.savedAdapter() == null) DiagTab.Link else DiagTab.Gauges
    var tab by rememberSaveable { mutableStateOf(initial) }
    var graphPid by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(onBack = onDismiss)

    KittPanel(
        title = "Diagnostics",
        modifier = modifier.pointerInput(Unit) { detectTapGestures {} },
        accent = LedGreen,
        trailing = {
            val (text, color) = when (val s = state) {
                is ObdState.Connected -> "LINKED · ${"%.1f".format(Locale.US, hz)} HZ" to LedGreen
                is ObdState.Connecting -> "CONNECTING" to DashAmber
                is ObdState.Error -> "LINK ERROR · RETRY ${s.retryInSeconds}S" to AlertRed
                ObdState.Idle -> "OFFLINE" to DashGrey
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DiagTab.entries.forEach { option ->
                    KittButton(
                        label = option.label,
                        onClick = { tab = option },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = LedGreen,
                        active = option == tab
                    )
                }
                KittButton("Close", onDismiss, Modifier.weight(1f).fillMaxHeight(), DashGrey)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    DiagTab.Gauges -> GaugesTab(app, onGraph = { pid ->
                        graphPid = pid
                        tab = DiagTab.Data
                    })
                    DiagTab.Data -> DataTab(app, graphPid)
                    DiagTab.Codes -> CodesTab(app, onAskKitt)
                    DiagTab.Vehicle -> VehicleTab(app)
                    DiagTab.Tools -> ToolsTab(app)
                    DiagTab.Pids -> PidsTab(app)
                    DiagTab.Link -> LinkTab(app)
                }
            }
        }
    }
}
