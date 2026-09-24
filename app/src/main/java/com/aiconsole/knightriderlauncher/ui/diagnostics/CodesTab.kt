package com.aiconsole.knightriderlauncher.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import androidx.compose.foundation.layout.Spacer
import com.aiconsole.knightriderlauncher.obd.DtcDecoder
import com.aiconsole.knightriderlauncher.obd.FreezeFrame
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Read and clear trouble codes; any code can be handed to KITT for a diagnosis. */
@Composable
fun CodesTab(app: KittApplication, onAskKitt: (String) -> Unit) {
    val report by app.obd.dtcs.collectAsState()
    val frame by app.obd.freezeFrame.collectAsState()
    val metric by app.settings.metricUnits.state.collectAsState()
    val state by app.obd.state.collectAsState()
    val connected = state is ObdState.Connected
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton(
                label = if (busy) "Reading…" else "Read codes",
                onClick = {
                    busy = true
                    scope.launch {
                        message = app.obd.readCodes().fold(
                            onSuccess = { "Scan complete: ${it.all.size} code(s)." },
                            onFailure = { it.message }
                        )
                        busy = false
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = LedGreen,
                active = true,
                enabled = connected && !busy
            )
            KittButton(
                label = "Clear codes",
                onClick = { confirmClear = true },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = AlertRed,
                enabled = connected && !busy && (report?.all?.isNotEmpty() == true)
            )
        }

        if (!connected) HelpText("Connect the OBD adapter on the LINK tab to read codes.", color = DashAmber)
        message?.let { HelpText(it) }

        if (confirmClear) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(AlertRed.copy(alpha = 0.12f))
                    .border(1.dp, AlertRed, MaterialTheme.shapes.small)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Clearing erases stored codes and freeze-frame data and resets emissions readiness monitors " +
                        "(the car may fail an inspection until they re-run). Engine off, ignition on. Continue?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashWhite
                )
                Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittButton("Cancel", { confirmClear = false }, Modifier.weight(1f).fillMaxHeight(), DashGrey)
                    KittButton(
                        label = "Clear now",
                        onClick = {
                            confirmClear = false
                            busy = true
                            scope.launch {
                                message = app.obd.clearCodes().fold(
                                    onSuccess = { "Codes cleared. ${it.all.size} remain." },
                                    onFailure = { it.message }
                                )
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = AlertRed,
                        active = true
                    )
                }
            }
        }

        val current = report
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentDisplay(
                text = current?.all?.size?.toString() ?: "--",
                modifier = Modifier.width(70.dp).height(44.dp),
                color = if ((current?.all?.size ?: 0) > 0) AlertRed else LedGreen,
                cells = 2
            )
            Column {
                Text(
                    text = "CHECK ENGINE LIGHT: " + when (current?.milOn) {
                        true -> "ON"
                        false -> "OFF"
                        null -> "UNKNOWN"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (current?.milOn == true) AlertRed else DashGrey
                )
                current?.let {
                    Text(
                        "Last scan ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.readAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DashGrey
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (current != null) {
                codeSection("Stored (confirmed)", current.stored, AlertRed, onAskKitt)
                codeSection("Pending", current.pending, DashAmber, onAskKitt)
                codeSection("Permanent", current.permanent, AlertRed, onAskKitt)
                if (current.all.isEmpty()) {
                    item { HelpText("No trouble codes. Every emissions module reports clean.", color = LedGreen) }
                }
                if (current.stored.isNotEmpty()) {
                    item(key = "freeze") {
                        FreezeFramePanel(
                            frame = frame,
                            metric = metric,
                            busy = busy,
                            enabled = connected,
                            onRead = {
                                busy = true
                                scope.launch {
                                    message = app.obd.readFreezeFrame().fold(
                                        onSuccess = { if (it == null) "This car stored no freeze frame." else null },
                                        onFailure = { it.message }
                                    )
                                    busy = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.codeSection(
    title: String,
    codes: List<String>,
    color: Color,
    onAskKitt: (String) -> Unit
) {
    if (codes.isEmpty()) return
    item(key = "h_$title") { SectionHeader(title, color = color) }
    items(codes, key = { "$title$it" }) { code ->
        val description = DtcDecoder.describe(code)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
                .border(1.dp, color.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(code, style = MaterialTheme.typography.headlineSmall, color = color)
            Text(description, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = DashWhite)
            KittButton(
                label = "Ask KITT",
                onClick = {
                    onAskKitt(
                        "Diagnose fault code $code ($description) on my vehicle. How serious is it, " +
                            "what are the most likely causes, and what should I check first? " +
                            "Use the freeze frame data if it was captured for this code."
                    )
                },
                modifier = Modifier.height(40.dp),
                color = ScannerRed,
                active = true
            )
        }
    }
}

/** Sensor snapshot from the moment the check-engine code set, like Car Scanner's freeze frame view. */
@Composable
private fun FreezeFramePanel(
    frame: FreezeFrame?,
    metric: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onRead: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(KittBlack)
            .border(1.dp, DashAmber.copy(alpha = 0.6f), MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(
                if (frame != null) "Freeze frame · ${frame.dtc}" else "Freeze frame",
                Modifier.weight(1f),
                color = DashAmber
            )
            KittButton(
                label = if (busy) "Reading…" else if (frame == null) "Read" else "Re-read",
                onClick = onRead,
                modifier = Modifier.height(40.dp).width(96.dp),
                color = DashAmber,
                enabled = enabled && !busy
            )
        }
        if (frame == null || frame.values.isEmpty()) {
            HelpText("Conditions the engine computer recorded when the code set (RPM, load, temperatures, trims).")
        } else {
            frame.values.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (def, value) ->
                        Row(Modifier.weight(1f)) {
                            Text(def.shortName.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = DashGrey)
                            Text(
                                "${ObdManager.formatValue(def.displayValue(value, metric), def)} ${def.unit(metric)}".trim(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = DashWhite
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
