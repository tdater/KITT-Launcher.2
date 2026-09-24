package com.aiconsole.knightriderlauncher.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.obd.GaugeConfig
import com.aiconsole.knightriderlauncher.obd.GaugeStyle
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.PidDefinition
import com.aiconsole.knightriderlauncher.obd.PidSource
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.gauges.GaugeTile
import com.aiconsole.knightriderlauncher.ui.gauges.GaugeUi
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import java.util.Locale

/** The custom gauge dashboard: live gauges with edit mode, an add-gauge picker and limit editor. */
@Composable
fun GaugesTab(app: KittApplication) {
    val gauges by app.gauges.gauges.collectAsState()
    val readings by app.obd.displayReadings.collectAsState()
    val defs by app.obd.definitions.collectAsState()
    val metric by app.settings.metricUnits.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var limitsFor by remember { mutableStateOf<GaugeConfig?>(null) }

    val editingLimits = limitsFor
    when {
        adding -> GaugePicker(app, metric, onDone = { adding = false })
        editingLimits != null -> LimitEditor(
            config = editingLimits,
            def = defs[editingLimits.pidId],
            metric = metric,
            onSave = {
                app.gauges.update(it)
                limitsFor = null
            },
            onCancel = { limitsFor = null }
        )
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton(
                    label = if (editing) "Done" else "Edit layout",
                    onClick = { editing = !editing },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = DashAmber,
                    active = editing
                )
                KittButton("+ Add gauge", { adding = true }, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = true)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(gauges, key = { it.id }) { config ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GaugeTile(
                            gauge = GaugeUi(config, defs[config.pidId], readings[config.pidId]),
                            metric = metric,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        )
                        if (editing) {
                            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                KittButton("<", { app.gauges.move(config.id, -1) }, Modifier.weight(1f).fillMaxHeight())
                                KittButton(">", { app.gauges.move(config.id, 1) }, Modifier.weight(1f).fillMaxHeight())
                                KittButton(
                                    label = "Style",
                                    onClick = {
                                        val next = GaugeStyle.entries[(config.style.ordinal + 1) % GaugeStyle.entries.size]
                                        app.gauges.update(config.copy(style = next))
                                    },
                                    modifier = Modifier.weight(1.4f).fillMaxHeight(),
                                    color = LedGreen
                                )
                                KittButton("Limit", { limitsFor = config }, Modifier.weight(1.4f).fillMaxHeight(), DashAmber)
                                KittButton("X", { app.gauges.remove(config.id) }, Modifier.weight(1f).fillMaxHeight(), AlertRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pick any available PID and a style to add a gauge. Shows live values while browsing. */
@Composable
private fun GaugePicker(app: KittApplication, metric: Boolean, onDone: () -> Unit) {
    val available by app.obd.available.collectAsState()
    val readings by app.obd.displayReadings.collectAsState()
    var query by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(GaugeStyle.BAR) }
    val filtered = available.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.shortName.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KittTextField(query, { query = it }, "Search sensors…", Modifier.weight(1f))
            KittButton("Cancel", onDone, Modifier.height(48.dp), DashGrey)
        }
        SectionHeader("Gauge style", color = DashAmber)
        ChoiceRow(GaugeStyle.entries, style, { it.label }, { style = it })
        SectionHeader("Sensor", color = DashAmber)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { def ->
                PidRow(def, readings[def.id]?.value, metric) {
                    app.gauges.add(def.id, style)
                    onDone()
                }
            }
        }
    }
}

@Composable
internal fun PidRow(def: PidDefinition, base: Double?, metric: Boolean, onClick: (() -> Unit)? = null) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittBezel, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(def.name, style = MaterialTheme.typography.bodyLarge, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val origin = when (def.source) {
                PidSource.STANDARD -> "OBD-II ${def.command}"
                PidSource.MANUFACTURER -> "Manufacturer ${def.command} @ ${def.header ?: "engine"}"
                PidSource.CUSTOM -> "Custom ${def.command}${def.header?.let { " @ $it" }.orEmpty()}"
                PidSource.ADAPTER -> "Adapter"
            }
            Text(origin, style = MaterialTheme.typography.labelSmall, color = DashGrey)
        }
        Text(
            text = base?.let { "${ObdManager.formatValue(def.displayValue(it, metric), def)} ${def.unit(metric)}" } ?: "--",
            style = MaterialTheme.typography.bodyLarge,
            color = if (base != null) DashAmber else DashGrey
        )
    }
}

/** Warning limits, entered in the driver's display units and stored in metric. */
@Composable
private fun LimitEditor(
    config: GaugeConfig,
    def: PidDefinition?,
    metric: Boolean,
    onSave: (GaugeConfig) -> Unit,
    onCancel: () -> Unit
) {
    fun show(base: Double?) = if (base == null || def == null) "" else
        "%.1f".format(Locale.US, def.displayValue(base, metric))
    var above by remember { mutableStateOf(show(config.warnAbove)) }
    var below by remember { mutableStateOf(show(config.warnBelow)) }
    val unit = def?.unit(metric).orEmpty()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Warning limits · ${def?.name ?: config.pidId}", color = DashAmber)
        HelpText("KITT flashes the gauge red and speaks up when the value crosses a limit. Leave blank for none.")
        Text("WARN ABOVE ($unit)", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        KittTextField(above, { above = it }, "none", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Decimal)
        Text("WARN BELOW ($unit)", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        KittTextField(below, { below = it }, "none", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Decimal)
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Cancel", onCancel, Modifier.weight(1f).fillMaxHeight(), DashGrey)
            KittButton(
                label = "Save",
                onClick = {
                    fun parse(text: String) = text.trim().toDoubleOrNull()?.let { v ->
                        def?.quantity?.fromDisplay(v, metric) ?: v
                    }
                    onSave(config.copy(warnAbove = parse(above), warnBelow = parse(below)))
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = LedGreen,
                active = true
            )
        }
    }
}
