package com.aiconsole.kittlauncher2.ui.diagnostics

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.ObdManager
import com.aiconsole.kittlauncher2.obd.ObdState
import com.aiconsole.kittlauncher2.obd.PidDefinition
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.SectionHeader
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_SERIES = 3
private val WINDOWS = listOf(30_000L, 60_000L, 300_000L)

private fun seriesColor(index: Int): Color = when (index) {
    0 -> LedGreen
    1 -> DashAmber
    else -> ScannerRed
}

/** Live graphs of up to three sensors, plus CSV recording and export. */
@Composable
fun DataTab(app: KittApplication, initialPid: String?) {
    val defs by app.obd.definitions.collectAsState()
    val gauges by app.gauges.gauges.collectAsState()
    val metric by app.settings.metricUnits.state.collectAsState()
    // Not read here: the chart and legend read it themselves so ticks only redraw them.
    val tick = app.obd.displayReadings.collectAsState()
    val live by app.obd.state.collectAsState()

    val candidates = remember(defs, gauges, tick.value.keys) {
        (gauges.map { it.pidId } + tick.value.keys).distinct().mapNotNull { defs[it] }
    }
    var selected by rememberSaveable {
        mutableStateOf(listOfNotNull(initialPid ?: gauges.firstOrNull()?.pidId))
    }
    var windowMs by rememberSaveable { mutableLongStateOf(60_000L) }
    val series = selected.mapNotNull { defs[it] }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Live graph", Modifier.weight(1f), color = LedGreen)
            WINDOWS.forEach { w ->
                KittButton(
                    label = if (w < 60_000L) "${w / 1000}s" else "${w / 60_000L}m",
                    onClick = { windowMs = w },
                    modifier = Modifier.padding(start = 6.dp).width(64.dp).height(40.dp),
                    color = LedGreen,
                    active = w == windowMs
                )
            }
        }
        LazyRow(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(candidates, key = { it.id }) { def ->
                val index = selected.indexOf(def.id)
                KittButton(
                    label = def.shortName,
                    onClick = {
                        selected = when {
                            index >= 0 -> selected - def.id
                            selected.size >= MAX_SERIES -> selected.drop(1) + def.id
                            else -> selected + def.id
                        }
                    },
                    modifier = Modifier.widthIn(min = 72.dp).height(44.dp),
                    color = if (index >= 0) seriesColor(index) else DashGrey,
                    active = index >= 0
                )
            }
        }
        if (candidates.isEmpty()) {
            HelpText("Graphs appear once sensors are reporting. Connect on the Link tab or add gauges.")
        }
        Legend(series, tick, metric)
        LiveChart(
            app = app,
            series = series,
            windowMs = windowMs,
            tick = tick,
            modifier = Modifier.fillMaxWidth().weight(0.55f)
        )
        Recorder(app, connected = live is ObdState.Connected, modifier = Modifier.fillMaxWidth().weight(0.45f))
    }
}

@Composable
private fun Legend(series: List<PidDefinition>, tick: State<Map<String, PidReading>>, metric: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        series.forEachIndexed { index, def ->
            val value = tick.value[def.id]?.value
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 4.dp).background(seriesColor(index)))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = def.shortName.uppercase() + "  " +
                        (value?.let { "${ObdManager.formatValue(def.displayValue(it, metric), def)} ${def.unit(metric)}".trim() } ?: "--"),
                    style = MaterialTheme.typography.labelMedium,
                    color = seriesColor(index),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Each series is scaled to its own sensor range, so different units can share one chart. */
@Composable
private fun LiveChart(
    app: KittApplication,
    series: List<PidDefinition>,
    windowMs: Long,
    tick: State<Map<String, PidReading>>,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    Canvas(
        modifier
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittBezel, shape)
            .padding(8.dp)
    ) {
        tick.value // Redraw on every display tick.
        for (k in 1..3) {
            val y = size.height * k / 4
            drawLine(KittBezel, Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (k in 1..5) {
            val x = size.width * k / 6
            drawLine(KittBezel.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        val now = System.currentTimeMillis()
        series.forEachIndexed { index, def ->
            val points = app.pidHistory.points(def.id, now, windowMs)
            if (points.size < 2) return@forEachIndexed
            val span = (def.max - def.min).takeIf { it > 0 } ?: 1.0
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = size.width * (1f - (now - p.timestamp).toFloat() / windowMs)
                val y = size.height * (1f - ((p.value - def.min) / span).toFloat().coerceIn(0f, 1f))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, seriesColor(index), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Recorder(app: KittApplication, connected: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by app.dataLogger.session.collectAsState()
    val files by app.dataLogger.files.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val file = exporting
        exporting = null
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                        ?: throw IOException("Couldn't open the destination")
                }
                "Saved ${file.name}."
            } catch (e: IOException) {
                "Export failed: ${e.message}"
            } catch (e: SecurityException) {
                "Export failed: ${e.message}"
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            val active = session
            Column(Modifier.weight(1f)) {
                SectionHeader("Data log", color = if (active != null) AlertRed else DashAmber)
                Text(
                    text = if (active != null) {
                        val secs = (System.currentTimeMillis() - active.startedAt) / 1000
                        "● REC %d:%02d · %d rows · %d sensors".format(Locale.US, secs / 60, secs % 60, active.rows, active.columns)
                    } else {
                        message ?: "Records every reporting sensor twice a second to a CSV file."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active != null) AlertRed else DashGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            KittButton(
                label = if (active != null) "Stop" else "Record",
                onClick = {
                    if (active != null) {
                        app.dataLogger.stop()
                        message = "Log saved."
                    } else {
                        message = app.dataLogger.start()
                    }
                },
                modifier = Modifier.width(120.dp).fillMaxHeight(),
                color = AlertRed,
                active = active != null,
                enabled = active != null || connected
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (files.isEmpty()) item { HelpText("No recordings yet.") }
            items(files, key = { it.name }) { file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .background(KittBlack)
                        .border(1.dp, KittBezel, MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp)
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = DashWhite, maxLines = 1)
                    Text("${(file.length() + 1023) / 1024} KB", style = MaterialTheme.typography.labelSmall, color = DashGrey)
                    val recording = session?.file == file
                    KittButton(
                        label = "Export",
                        onClick = {
                            exporting = file
                            export.launch(file.name)
                        },
                        modifier = Modifier.width(88.dp).fillMaxHeight(0.8f),
                        color = LedGreen,
                        enabled = !recording
                    )
                    KittButton(
                        label = "Delete",
                        onClick = { app.dataLogger.delete(file) },
                        modifier = Modifier.width(88.dp).fillMaxHeight(0.8f),
                        color = AlertRed,
                        enabled = !recording
                    )
                }
            }
        }
    }
}
