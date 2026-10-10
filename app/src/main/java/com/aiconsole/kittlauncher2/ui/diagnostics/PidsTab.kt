package com.aiconsole.kittlauncher2.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.CustomPidStore
import com.aiconsole.kittlauncher2.obd.FormulaEvaluator
import com.aiconsole.kittlauncher2.obd.PidDefinition
import com.aiconsole.kittlauncher2.obd.PidSource
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.components.KittToggle
import com.aiconsole.kittlauncher2.ui.components.SectionHeader
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import kotlinx.coroutines.launch

private const val WATCH_KEY = "diagnostics_pids"

/** Manufacturer PIDs, custom PID editor, and an all-sensors live list. */
@Composable
fun PidsTab(app: KittApplication) {
    val profile by app.obd.profile.collectAsState()
    val custom by app.customPids.pids.collectAsState()
    val available by app.obd.available.collectAsState()
    val readings by app.obd.displayReadings.collectAsState()
    val metric by app.settings.metricUnits.state.collectAsState()
    val mfrEnabled by app.settings.manufacturerPids.state.collectAsState()
    var showAll by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PidDefinition?>(null) }
    var creating by remember { mutableStateOf(false) }

    // Poll what this tab shows; the all-sensors view costs refresh rate, so it's opt-in.
    val watched = (profile.pids.takeIf { mfrEnabled }.orEmpty() + custom +
        if (showAll) available.filter { it.source == PidSource.STANDARD } else emptyList()).map { it.id }.toSet()
    DisposableEffect(watched) {
        app.obd.watch(WATCH_KEY, watched)
        onDispose { app.obd.unwatch(WATCH_KEY) }
    }

    val editTarget = editing
    if (creating || editTarget != null) {
        CustomPidEditor(app, editTarget, onDone = {
            creating = false
            editing = null
        })
        return
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionHeader("Manufacturer · ${profile.name}", color = DashAmber) }
        item {
            KittToggle(
                label = "Manufacturer PIDs",
                checked = mfrEnabled,
                onCheckedChange = { app.settings.manufacturerPids.set(it) },
                description = profile.notes.ifEmpty { null }
            )
        }
        if (mfrEnabled) {
            if (profile.pids.isEmpty()) {
                item { HelpText("No built-in extended PIDs for this make yet. Add your own below.") }
            }
            items(profile.pids, key = { "m_${it.id}" }) { def ->
                Column {
                    PidRow(def, readings[def.id]?.value, metric)
                    def.note?.let { HelpText(it) }
                }
            }
        }

        item { SectionHeader("Custom PIDs", color = LedGreen) }
        item {
            KittButton(
                label = "+ New custom PID",
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                color = LedGreen,
                active = true
            )
        }
        items(custom, key = { "c_${it.id}" }) { def ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) { PidRow(def, readings[def.id]?.value, metric) }
                KittButton("Edit", { editing = def }, Modifier.width(64.dp).height(44.dp))
                KittButton("X", { app.customPids.remove(def.id) }, Modifier.width(44.dp).height(44.dp), AlertRed)
            }
        }

        item { SectionHeader("All live sensors", color = DashAmber) }
        item {
            KittToggle(
                label = "Poll every supported sensor",
                checked = showAll,
                onCheckedChange = { showAll = it },
                description = "Lowers the refresh rate of your gauges while open."
            )
        }
        if (showAll) {
            items(available.filter { it.source == PidSource.STANDARD }, key = { "s_${it.id}" }) { def ->
                PidRow(def, readings[def.id]?.value, metric)
            }
        }
    }
}

/** Torque/Car Scanner-style custom PID form with a live TEST against the car. */
@Composable
private fun CustomPidEditor(app: KittApplication, existing: PidDefinition?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var command by remember { mutableStateOf(existing?.command.orEmpty()) }
    var header by remember { mutableStateOf(existing?.header.orEmpty()) }
    var formula by remember { mutableStateOf(existing?.formula ?: "A") }
    var unit by remember { mutableStateOf(existing?.customUnit.orEmpty()) }
    var min by remember { mutableStateOf(existing?.min?.toString() ?: "0") }
    var max by remember { mutableStateOf(existing?.max?.toString() ?: "100") }
    var fast by remember { mutableStateOf(existing?.fast ?: false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    val formulaError = FormulaEvaluator.validate(formula)
    val commandOk = command.length >= 4 && command.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
    val valid = name.isNotBlank() && commandOk && formulaError == null &&
        min.toDoubleOrNull() != null && max.toDoubleOrNull() != null

    fun build() = CustomPidStore.custom(
        id = existing?.id ?: ("custom_" + java.util.UUID.randomUUID().toString().take(8)),
        name = name.trim(),
        shortName = name.trim().take(12),
        command = command,
        formula = formula,
        unit = unit.trim(),
        min = min.toDoubleOrNull() ?: 0.0,
        max = max.toDoubleOrNull() ?: 100.0,
        header = header,
        fast = fast
    )

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionHeader(if (existing == null) "New custom PID" else "Edit custom PID", color = LedGreen) }
        item {
            HelpText(
                "Command is the full request in hex, e.g. 221940 (Mode 22, PID 1940). Header targets an ECU " +
                    "(7E0 engine, 7E1/7E2 transmission); leave blank for the engine. Formula uses A, B, C… for the " +
                    "response bytes, e.g. ((A*256)+B)/4 or signed(A)-40."
            )
        }
        item { Field("Name") { KittTextField(name, { name = it }, "Transmission temp", Modifier.fillMaxWidth()) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Command (hex)", Modifier.weight(1f)) {
                    KittTextField(command, { command = it.uppercase() }, "221940", Modifier.fillMaxWidth())
                }
                Field("Header", Modifier.weight(1f)) {
                    KittTextField(header, { header = it.uppercase() }, "7E0", Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Column {
                Field("Formula") { KittTextField(formula, { formula = it }, "A-40", Modifier.fillMaxWidth()) }
                formulaError?.let { HelpText(it, color = AlertRed) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Unit", Modifier.weight(1f)) { KittTextField(unit, { unit = it }, "°C", Modifier.fillMaxWidth()) }
                Field("Min", Modifier.weight(1f)) {
                    KittTextField(min, { min = it }, "0", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Decimal)
                }
                Field("Max", Modifier.weight(1f)) {
                    KittTextField(max, { max = it }, "100", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Decimal)
                }
            }
        }
        item {
            KittToggle("Fast refresh", fast, { fast = it }, description = "Poll every cycle instead of every fifth.")
        }
        item {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton("Cancel", onDone, Modifier.weight(1f).fillMaxHeight(), DashGrey)
                KittButton(
                    label = "Test",
                    onClick = {
                        testResult = "Testing…"
                        scope.launch { testResult = app.obd.testPid(build()) }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = DashAmber,
                    enabled = valid
                )
                KittButton(
                    label = "Save",
                    onClick = {
                        app.customPids.upsert(build())
                        onDone()
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = LedGreen,
                    active = true,
                    enabled = valid
                )
            }
        }
        testResult?.let { item { HelpText(it, color = DashAmber) } }
    }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = DashGrey)
        content()
    }
}
