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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.obd.DtcDecoder
import com.aiconsole.knightriderlauncher.obd.MonitorTest
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PerformanceResult
import com.aiconsole.knightriderlauncher.obd.PerformanceRun
import com.aiconsole.knightriderlauncher.obd.PerformanceTimer
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class Tool(val label: String) { Performance("Performance"), Monitors("Mode 06"), Modules("Modules"), Adapter("Adapter") }

/** Performance timers, on-board monitor tests, module scan, and adapter tools. All read-only. */
@Composable
fun ToolsTab(app: KittApplication) {
    var tool by rememberSaveable { mutableStateOf(Tool.Performance) }
    val connected = app.obd.state.collectAsState().value is ObdState.Connected
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow(Tool.entries, tool, { it.label }, { tool = it }, color = LedGreen)
        if (!connected && tool != Tool.Adapter) HelpText("Connect to the vehicle first (Link tab).", color = DashAmber)
        when (tool) {
            Tool.Performance -> PerformanceTool(app)
            Tool.Monitors -> MonitorTool(app, connected)
            Tool.Modules -> ModuleTool(app, connected)
            Tool.Adapter -> AdapterTool(app, connected)
        }
    }
}

@Composable
private fun PerformanceTool(app: KittApplication) {
    val metric by app.settings.metricUnits.state.collectAsState()
    val readings by app.obd.readings.collectAsState()
    var run by rememberSaveable { mutableStateOf(PerformanceRun.ZeroToSixty) }
    var timer by remember(run, metric) { mutableStateOf(PerformanceTimer(run, metric)) }
    // The timer is a plain object; mirror what the UI shows into snapshot state after each sample.
    var phase by remember(timer) { mutableStateOf(timer.phase) }
    var seconds by remember(timer) { mutableDoubleStateOf(0.0) }
    var metres by remember(timer) { mutableDoubleStateOf(0.0) }
    val results = remember { mutableStateListOf<PerformanceResult>() }

    val speed = readings[PidCatalog.SPEED]
    LaunchedEffect(timer, speed?.timestamp) {
        speed ?: return@LaunchedEffect
        val before = timer.phase
        timer.feed(speed.timestamp, speed.value)
        if (timer.phase == PerformanceTimer.Phase.Done && before != PerformanceTimer.Phase.Done) timer.result?.let { results.add(0, it) }
        phase = timer.phase
        seconds = timer.result?.seconds ?: timer.elapsedS
        metres = timer.result?.distanceM ?: timer.distanceM
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow(PerformanceRun.entries, run, { if (metric) it.metricLabel else it.label }, { run = it }, color = ScannerRed)
        val status = when (phase) {
            PerformanceTimer.Phase.WaitingToArm ->
                if (run == PerformanceRun.SixtyToZero) "Get above the start speed, then brake hard when safe." else "Come to a complete stop."
            PerformanceTimer.Phase.Armed -> if (run == PerformanceRun.SixtyToZero) "Armed: timing starts as you brake through the start speed." else "Armed: timing starts when you move."
            PerformanceTimer.Phase.Running -> "Timing…"
            PerformanceTimer.Phase.Done -> "Done."
        }
        Text(status.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (phase == PerformanceTimer.Phase.Running) ScannerRed else DashAmber)
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentDisplay("%.2f".format(Locale.US, seconds), Modifier.weight(1f).fillMaxHeight(), ScannerRed, cells = 5)
            SegmentDisplay(
                "%.0f".format(Locale.US, if (metric) metres else metres * 3.28084),
                Modifier.weight(1f).fillMaxHeight(),
                DashAmber,
                cells = 5
            )
        }
        Row {
            Text("SECONDS", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey)
            Text(if (metric) "METRES" else "FEET", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey)
        }
        KittButton("Reset", { timer = PerformanceTimer(run, metric) }, Modifier.fillMaxWidth().height(48.dp), DashAmber)
        results.take(8).forEach { r ->
            Text(
                "%s  %.2f s  %.0f %s  trap %.0f %s".format(
                    Locale.US,
                    if (metric) r.run.metricLabel else r.run.label,
                    r.seconds,
                    if (metric) r.distanceM else r.distanceM * 3.28084,
                    if (metric) "m" else "ft",
                    if (metric) r.trapKmh else r.trapKmh / 1.609344,
                    if (metric) "km/h" else "mph"
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = DashWhite
            )
        }
        HelpText(
            "Use only on a closed course or where it's legal and safe. Times come from OBD speed, " +
                "so they're indicative (roughly ±0.2 s)."
        )
    }
}

@Composable
private fun MonitorTool(app: KittApplication, connected: Boolean) {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<MonitorTest>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KittButton(
            if (busy) "Reading…" else "Read monitor tests",
            {
                busy = true
                scope.launch {
                    app.obd.readMonitorTests().fold({ results = it; message = if (it.isEmpty()) "The ECU reported no Mode 06 tests." else null }, { message = it.message })
                    busy = false
                }
            },
            Modifier.fillMaxWidth().height(48.dp),
            LedGreen,
            active = true,
            enabled = connected && !busy
        )
        message?.let { HelpText(it, color = DashAmber) }
        HelpText("The ECU's own self-test results against its limits. Misfire counts per cylinder help pinpoint a rough-running cylinder.")
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results.orEmpty()) { t ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(KittBlack)
                        .border(1.dp, if (t.passed) KittBezel else AlertRed, MaterialTheme.shapes.small).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.monitor.uppercase(), style = MaterialTheme.typography.labelLarge, color = DashWhite)
                        Text(t.test, style = MaterialTheme.typography.labelSmall, color = DashGrey)
                    }
                    Text(
                        "${fmt(t.value)} ${t.unit}  [${fmt(t.min)}–${fmt(t.max)}]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DashWhite
                    )
                    Text(if (t.passed) "  PASS" else "  FAIL", style = MaterialTheme.typography.labelLarge, color = if (t.passed) LedGreen else AlertRed)
                }
            }
        }
    }
}

private fun fmt(v: Double): String = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e7) v.toLong().toString() else "%.3f".format(Locale.US, v)

@Composable
private fun ModuleTool(app: KittApplication, connected: Boolean) {
    val scope = rememberCoroutineScope()
    var modules by remember { mutableStateOf<List<ObdManager.ModuleCodes>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KittButton(
            if (busy) "Scanning…" else "Scan modules",
            {
                busy = true
                scope.launch {
                    app.obd.scanModules().fold({ modules = it; message = null }, { message = it.message })
                    busy = false
                }
            },
            Modifier.fillMaxWidth().height(48.dp),
            LedGreen,
            active = true,
            enabled = connected && !busy
        )
        message?.let { HelpText(it, color = DashAmber) }
        HelpText("Finds the emissions-related modules that answer on the diagnostic bus and reads each one's codes. Read-only.")
        modules?.forEach { m ->
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(KittBlack)
                    .border(1.dp, if (m.stored.isEmpty()) KittBezel else AlertRed, MaterialTheme.shapes.small).padding(8.dp)
            ) {
                Text("${m.module.name} · ${m.module.responseId}".uppercase(), style = MaterialTheme.typography.labelLarge, color = DashWhite)
                val codes = m.stored.map { "$it (stored)" } + m.pending.map { "$it (pending)" }
                if (codes.isEmpty()) Text("No codes", style = MaterialTheme.typography.bodyMedium, color = LedGreen)
                codes.forEach { c ->
                    Text("$c  ${DtcDecoder.describe(c.substringBefore(' '))}", style = MaterialTheme.typography.bodyMedium, color = DashAmber)
                }
            }
        }
    }
}

@Composable
private fun AdapterTool(app: KittApplication, connected: Boolean) {
    val scope = rememberCoroutineScope()
    val events by app.obd.linkEvents.collectAsState()
    var details by remember { mutableStateOf<ObdManager.AdapterDetails?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf("") }
    val transcript = remember { mutableStateListOf<String>() }

    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            KittButton(
                "Identify adapter",
                { scope.launch { app.obd.adapterDetails().fold({ details = it; message = null }, { message = it.message }) } },
                Modifier.fillMaxWidth().height(48.dp),
                LedGreen,
                active = true,
                enabled = connected
            )
        }
        details?.let { d ->
            item {
                Column {
                    Text(d.assessment.chip, style = MaterialTheme.typography.titleMedium, color = DashWhite)
                    Text(d.assessment.verdict, style = MaterialTheme.typography.bodyMedium, color = DashAmber)
                    d.voltage?.let { Text("Supply: %.1f V".format(Locale.US, it), style = MaterialTheme.typography.bodyMedium, color = LedGreen) }
                }
            }
        }
        message?.let { item { HelpText(it, color = DashAmber) } }
        item { SectionHeader("Console", color = DashAmber) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                KittTextField(command, { command = it }, "e.g. ATRV or 010C", Modifier.weight(1f))
                KittButton(
                    "Send",
                    {
                        val cmd = command.trim()
                        scope.launch {
                            val reply = app.obd.console(cmd).fold({ it.trim() }, { "✕ ${it.message}" })
                            transcript.add(0, "> $cmd\n$reply")
                        }
                        command = ""
                    },
                    Modifier.height(48.dp).width(80.dp),
                    LedGreen,
                    enabled = connected && command.isNotBlank()
                )
            }
        }
        item { HelpText("AT commands and read-only services (01 02 03 05 06 07 09 0A 19 22). Anything that clears codes or writes to the vehicle is refused.") }
        items(transcript.take(20)) { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = DashWhite)
        }
        item { SectionHeader("Connection log", color = DashAmber) }
        if (events.isEmpty()) item { HelpText("No connection attempts yet.") }
        items(events.asReversed().take(40)) { e ->
            Text(
                "${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(e.timestamp))}  ${e.layer.label.uppercase()}  ${e.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (e.ok) DashGrey else AlertRed
            )
        }
    }
}
