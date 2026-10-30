package com.aiconsole.kittlauncher2.ui.panes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.PidCatalog
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.components.CockpitSkin
import com.aiconsole.kittlauncher2.ui.components.LedBarGauge
import com.aiconsole.kittlauncher2.ui.components.VehicleStatusHud
import com.aiconsole.kittlauncher2.ui.components.skinFrame
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.DashYellow
import com.aiconsole.kittlauncher2.ui.theme.FilmEdition
import com.aiconsole.kittlauncher2.ui.theme.FilmHudState
import com.aiconsole.kittlauncher2.ui.theme.FilmHudText
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.LiveTelemetrySnapshot
import com.aiconsole.kittlauncher2.ui.theme.LocalLiteMode
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import com.aiconsole.kittlauncher2.ui.theme.ThemeState
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val Mono = FontFamily.Monospace

private val FILM_DRIVE_PIDS = PidCatalog.TIRES.toSet() + setOf(
    PidCatalog.COOLANT,
    PidCatalog.MODULE_VOLTAGE,
    PidCatalog.ADAPTER_VOLTAGE,
    PidCatalog.FUEL_LEVEL,
    PidCatalog.ENGINE_LOAD,
    PidCatalog.THROTTLE,
    PidCatalog.INTAKE_TEMP,
    PidCatalog.RPM,
    PidCatalog.SPEED,
    PidCatalog.MAF
)

/**
 * The landscape Drive screen for the film themes, built to each film's visual design and edition:
 * a live telemetry strip across the top, three titled panels (live machine code / VT220 console /
 * OCP Directives + patrol telemetry on the left, tactical map in the center, and 3D X-ray hologram
 * vehicle scanner on the right) and an interactive tactical telemetry & voice bar along the bottom.
 */
@Composable
fun FilmDrivePage(
    skin: CockpitSkin,
    map: @Composable (Modifier) -> Unit,
    hud: () -> HudState,
    reading: (String) -> PidReading?,
    metric: Boolean,
    kittSpeaking: Boolean,
    modifier: Modifier = Modifier,
    onAssistant: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onPursuit: () -> Unit = {}
) {
    val kitt = LocalContext.current.applicationContext as KittApplication
    DisposableEffect(Unit) {
        kitt.obd.watch("film_drive", FILM_DRIVE_PIDS)
        onDispose { kitt.obd.unwatch("film_drive") }
    }
    val hz by kitt.obd.refreshHz.collectAsState()
    val dtcReport by kitt.obd.dtcs.collectAsState()
    val lite = LocalLiteMode.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lite) {
        if (lite) return@LaunchedEffect
        while (true) {
            delay(350)
            tick = (tick + 1) and 0x7FFF
        }
    }

    val state = hud()
    val edition = ThemeState.edition
    fun v(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 120_000 }?.value
    fun tempInt(c: Double) = if (metric) c.roundToInt() else (c * 9.0 / 5.0 + 32.0).roundToInt()
    val tempUnit = if (metric) "°C" else "°F"

    val coolantC = v(PidCatalog.COOLANT)
    val intakeC = v(PidCatalog.INTAKE_TEMP)
    val volts = v(PidCatalog.MODULE_VOLTAGE) ?: v(PidCatalog.ADAPTER_VOLTAGE)
    val fuel = v(PidCatalog.FUEL_LEVEL)
    val load = v(PidCatalog.ENGINE_LOAD)
    val throttle = v(PidCatalog.THROTTLE)
    val rpmInt = v(PidCatalog.RPM)?.roundToInt() ?: state.rpmText?.filter { it.isDigit() }?.toIntOrNull()
    val speedInt = state.speed?.toDoubleOrNull()?.roundToInt() ?: v(PidCatalog.SPEED)?.let {
        if (metric) it.roundToInt() else (it * 0.621371).roundToInt()
    }

    val snap = LiveTelemetrySnapshot(
        rpm = rpmInt,
        speed = speedInt,
        speedUnit = state.speedUnit,
        coolantTemp = coolantC?.let(::tempInt),
        tempUnit = tempUnit,
        throttlePct = throttle?.roundToInt(),
        loadPct = load?.roundToInt(),
        volts = volts,
        fuelPct = fuel?.roundToInt(),
        intakeTemp = intakeC?.let(::tempInt),
        hz = hz.toDouble(),
        dtcCount = dtcReport?.all?.size ?: 0,
        linked = state.linked,
        speedLimit = state.speedLimit,
        overLimit = state.overLimit,
        speaking = kittSpeaking,
        tick = tick
    )

    LaunchedEffect(snap) {
        FilmHudState.latestSnapshot = snap
    }

    if (skin == CockpitSkin.Terminator) {
        DisposableEffect(Unit) {
            FilmHudState.driveShowsCode = true
            onDispose { FilmHudState.driveShowsCode = false }
        }
    }

    val leftTitle = when (skin) {
        CockpitSkin.Terminator -> when (edition) {
            FilmEdition.T1000 -> "M-1000 POLYALLOY HEX STREAM"
            FilmEdition.FutureWar -> "SKYNET HK-NET TELEMETRY"
            else -> "6502 CSM-101 LIVE DISASSEMBLY"
        }
        CockpitSkin.Matrix -> when (edition) {
            FilmEdition.RealWorld -> "MJORLNIR EM FIELD CONSOLE"
            FilmEdition.Zion -> "ZION DOCK CONTROL TERMINAL"
            else -> "VT220 OPERATOR TERMINAL"
        }
        CockpitSkin.RoboCop -> ""
        else -> "SYSTEM LOG"
    }
    val mapTitle = when (skin) {
        CockpitSkin.Terminator -> when (edition) {
            FilmEdition.T1000 -> "LAPD PATROL GRID // TARGET TRACKER"
            FilmEdition.FutureWar -> "SECTOR 2029 // HK TACTICAL GRID"
            else -> "MILITARY WIREFRAME TARGETING MAP"
        }
        CockpitSkin.Matrix -> when (edition) {
            FilmEdition.RealWorld -> "SENTINEL PROXIMITY SONAR MAP"
            FilmEdition.Zion -> "ZION SECTOR DEFENSE RADAR"
            else -> "VECTOR RADAR SONAR NIGHT MAP"
        }
        else -> when (edition) {
            FilmEdition.Ed209 -> "OCP URBAN PACIFICATION GRID"
            FilmEdition.DeltaCity -> "DELTA CITY EXECUTIVE SECTOR MAP"
            else -> "OCP METRO WEST TACTICAL MAP"
        }
    }
    val rightTitle = when (skin) {
        CockpitSkin.Terminator -> when (edition) {
            FilmEdition.T1000 -> "POLYALLOY CHASSIS MIMETIC SCAN"
            FilmEdition.FutureWar -> "HK GROUND UNIT X-RAY SCANNER"
            else -> "T-800 CHASSIS DIAGNOSTIC SCANNER"
        }
        CockpitSkin.Matrix -> when (edition) {
            FilmEdition.RealWorld -> "HOVERCRAFT HULL & FIELD SCAN"
            FilmEdition.Zion -> "APU & MECHANICAL TELEMETRY"
            else -> "CONSTRUCT VEHICLE SCAN"
        }
        else -> ""
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (skin) {
            CockpitSkin.Terminator -> TerminatorTop(state, snap, edition)
            CockpitSkin.Matrix -> MatrixTop(snap, edition)
            CockpitSkin.RoboCop -> CopVisorBar(state, snap, edition, Modifier.fillMaxWidth().height(54.dp))
            else -> Unit
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilmPanel(
                leftTitle,
                Modifier.weight(0.72f).fillMaxHeight()
            ) {
                when (skin) {
                    CockpitSkin.Terminator -> MachineCode(snap, edition)
                    CockpitSkin.Matrix -> Vt220(snap, edition)
                    else -> Directives(snap, edition)
                }
            }
            FilmPanel(
                mapTitle,
                Modifier.weight(0.86f).fillMaxHeight()
            ) {
                map(Modifier.fillMaxSize())
                TacticalBrackets()
            }
            FilmPanel(
                rightTitle,
                Modifier.weight(1.72f).fillMaxHeight(),
                hazard = skin == CockpitSkin.RoboCop
            ) {
                VehicleStatusHud(reading, metric, { state.rpmFraction }, Modifier.fillMaxSize())
            }
        }
        when (skin) {
            CockpitSkin.Terminator -> TerminatorBottom(
                snap = snap,
                edition = edition,
                onAssistant = onAssistant,
                onDiagnostics = onDiagnostics,
                onPursuit = onPursuit,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            )
            CockpitSkin.Matrix -> MatrixBottom(
                snap = snap,
                edition = edition,
                onAssistant = onAssistant,
                onDiagnostics = onDiagnostics,
                onPursuit = onPursuit,
                modifier = Modifier.fillMaxWidth().height(66.dp)
            )
            CockpitSkin.RoboCop -> CopDispatch(
                snap = snap,
                edition = edition,
                onAssistant = onAssistant,
                onDiagnostics = onDiagnostics,
                onPursuit = onPursuit,
                modifier = Modifier.fillMaxWidth().height(66.dp)
            )
            else -> Unit
        }
    }
}

/** A titled, film-framed panel; [hazard] swaps the frame for RoboCop's yellow and black warning border. */
@Composable
private fun FilmPanel(title: String, modifier: Modifier, hazard: Boolean = false, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.small
    if (hazard) {
        Box(
            modifier.clip(shape).drawBehind {
                val w = 14.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(DashYellow, Offset(x, size.height), Offset(x + size.height, 0f), w)
                    x += w * 2f
                }
            }.padding(7.dp)
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF020608)).padding(8.dp)) { content() }
        }
    } else {
        Box(modifier.clip(shape).background(Color.Black.copy(alpha = 0.65f)).skinFrame(shape, ScannerRed)) {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                if (title.isNotEmpty()) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ScannerRed,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
            }
        }
    }
}

// ---------------------------------------------------------------- The Terminator

@Composable
private fun TerminatorTop(state: HudState, snap: LiveTelemetrySnapshot, edition: FilmEdition?) {
    val leftHeader = when (edition) {
        FilmEdition.T1000 -> "POLYALLOY TACH LATTICE"
        FilmEdition.FutureWar -> "HK-NET TURBINE OUTPUT"
        else -> "CSM-101 TACH SCANNER"
    }
    val rightHeader = when (edition) {
        FilmEdition.T1000 -> "PURSUIT INTERCEPT TRACKER"
        FilmEdition.FutureWar -> "SKYNET VELOCITY VECTOR"
        else -> "20-SEG TARGETING TRACKER"
    }
    val rpmValue = snap.rpm?.let { "%,d RPM".format(Locale.US, it) } ?: "-- RPM"
    val spdValue = "${snap.speed ?: "--"} ${snap.speedUnit}" + (snap.speedLimit?.let { " // LIM $it" } ?: "")
    val systemBanner = when {
        snap.dtcCount > 0 -> "SYSTEM ALERT: ${snap.dtcCount} ECU FAULT CODE(S) DETECTED // ${state.linkLabel}"
        state.linked -> "${edition?.brand ?: "CYBERDYNE SYSTEMS SERIES 800"} // BUS LOCKED (%.1f HZ)".format(Locale.US, snap.hz)
        else -> "${edition?.brand ?: "CYBERDYNE SYSTEMS SERIES 800"} // PASSIVE SCAN (NO OBD LINK)"
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(leftHeader, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono, maxLines = 1)
                    Text(rpmValue, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DashWhite, fontFamily = Mono, maxLines = 1)
                }
                LedBarGauge(state.rpmFraction, Modifier.fillMaxWidth().height(20.dp), segments = 20, color = ScannerRed)
            }
            Canvas(Modifier.width(46.dp).height(38.dp)) {
                val c = center
                val pulseR = 12.dp.toPx() + (snap.tick % 4) * 1.1.dp.toPx()
                drawCircle(ScannerRed.copy(alpha = 0.35f), pulseR, c, style = Stroke(1.dp.toPx()))
                drawCircle(ScannerRed, 11.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
                drawCircle(ScannerRed, 3.dp.toPx(), c)
                drawLine(ScannerRed, Offset(c.x - 20.dp.toPx(), c.y), Offset(c.x + 20.dp.toPx(), c.y), 1.5.dp.toPx())
                drawLine(ScannerRed, Offset(c.x, c.y - 17.dp.toPx()), Offset(c.x, c.y + 17.dp.toPx()), 1.5.dp.toPx())
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(spdValue, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (snap.overLimit) AlertRed else DashWhite, fontFamily = Mono, maxLines = 1)
                    Text(rightHeader, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono, maxLines = 1)
                }
                val speedFrac = ((snap.speed ?: 0).toFloat() / if (snap.speedUnit.contains("KM", ignoreCase = true)) 200f else 120f).coerceIn(0f, 1f)
                LedBarGauge(speedFrac, Modifier.fillMaxWidth().height(20.dp), segments = 20, color = if (snap.overLimit) AlertRed else ScannerRed)
            }
        }
        Text(
            systemBanner,
            style = MaterialTheme.typography.labelSmall,
            color = if (snap.dtcCount > 0) AlertRed else ScannerRed,
            fontFamily = Mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Live 6502 / Cyberdyne / Polyalloy / Skynet machine-code panel on the left side of the Terminator Drive screen.
 * Displays live CPU registers at the top and real-time sensor-encoded opcodes & hex memory dumps below,
 * with a stepping instruction pointer highlight bar.
 */
@Composable
private fun MachineCode(snap: LiveTelemetrySnapshot, edition: FilmEdition?) {
    val regs = remember(snap, edition) { FilmHudText.cpuRegisters(snap, edition) }
    val lines = remember(snap, edition) { FilmHudText.liveTerminatorCode(snap, edition) }
    val activeIdx = 1 + (snap.tick % (lines.size - 1).coerceAtLeast(1))
    Column(Modifier.fillMaxSize().clipToBounds(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(ScannerRed.copy(alpha = 0.16f))
                .border(1.dp, ScannerRed.copy(alpha = 0.55f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                regs,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = DashWhite,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            lines.forEachIndexed { idx, line ->
                val isCurrent = idx == activeIdx
                val lineColor = when {
                    idx == 0 -> DashAmber
                    line.startsWith("THREAT") && (snap.dtcCount > 0 || snap.overLimit) -> AlertRed
                    isCurrent -> DashWhite
                    else -> ScannerRed
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) ScannerRed.copy(alpha = 0.18f) else Color.Transparent)
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isCurrent) "▸" else " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = DashWhite,
                        fontFamily = Mono
                    )
                    Text(
                        line,
                        style = MaterialTheme.typography.labelSmall,
                        color = lineColor,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

/**
 * Replaces the old empty Cyberdyne status bracket box at the bottom of the Terminator Drive screen with a
 * 3-module Cyberdyne Tactical Telemetry & Command Console:
 * 1. Left: Power Cell & Thermal Core live readouts (Voltage, Coolant, Engine Load, Throttle) — tap for Diagnostics.
 * 2. Center: Model 101 / T-1000 / Skynet Neural Voice Waveform + Threat/DTC & OBD Hz readout — tap for Assistant.
 * 3. Right: Hardware-framed tactical override buttons (SCAN ECU, PURSUIT, COMMS).
 */
@Composable
private fun TerminatorBottom(
    snap: LiveTelemetrySnapshot,
    edition: FilmEdition?,
    onAssistant: () -> Unit,
    onDiagnostics: () -> Unit,
    onPursuit: () -> Unit,
    modifier: Modifier
) {
    val loc = Locale.US
    val cellStr = snap.volts?.let { "%.1fV".format(loc, it) } ?: "--V"
    val cellFrac = (((snap.volts ?: 11.5) - 11.0) / 3.8).toFloat().coerceIn(0f, 1f)
    val thermStr = snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "--"
    val thermFrac = ((snap.coolantTemp ?: 0).toFloat() / if (snap.tempUnit == "°C") 125f else 255f).coerceIn(0f, 1f)
    val loadStr = snap.loadPct?.let { "$it%" } ?: "--%"
    val loadFrac = ((snap.loadPct ?: 0) / 100f).coerceIn(0f, 1f)
    val tpsStr = snap.throttlePct?.let { "$it%" } ?: "--%"
    val tpsFrac = ((snap.throttlePct ?: 0) / 100f).coerceIn(0f, 1f)

    val voiceTitle = when (edition) {
        FilmEdition.T1000 -> "T-1000 MIMETIC AUDIO SYNTH"
        FilmEdition.FutureWar -> "SKYNET HK-NET TAC COMMS"
        else -> "MODEL 101 VOICE TERMINAL"
    }
    val statusTag = when {
        snap.dtcCount > 0 -> "THREAT: ${snap.dtcCount} DTC"
        snap.linked -> "BUS %.1f HZ // CLEAR".format(loc, snap.hz)
        else -> "OBD STANDBY"
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Module 1: Power Cell & Thermal Core Telemetry
        FilmPanel("POWER CELL & THERMAL CORE", Modifier.weight(1.25f).fillMaxHeight().clickable(onClick = onDiagnostics)) {
            Row(Modifier.fillMaxSize().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TerminatorMiniGauge("CELL", cellStr, cellFrac, Modifier.weight(1f))
                TerminatorMiniGauge("THERM", thermStr, thermFrac, Modifier.weight(1f))
                TerminatorMiniGauge("LOAD", loadStr, loadFrac, Modifier.weight(1f))
                TerminatorMiniGauge("TPS", tpsStr, tpsFrac, Modifier.weight(1f))
            }
        }
        // Module 2: Cyberdyne Voice Terminal & Threat Matrix (framed with Cyberdyne brackets)
        FilmPanel("", Modifier.weight(1.45f).fillMaxHeight().clickable(onClick = onAssistant)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    val t = 2.dp.toPx()
                    val b = 12.dp.toPx()
                    val left = 4.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val top = 2.dp.toPx()
                    val bottom = size.height - 2.dp.toPx()
                    for ((x, dir) in listOf(left to 1f, right to -1f)) {
                        drawLine(ScannerRed, Offset(x, top), Offset(x, bottom), t)
                        drawLine(ScannerRed, Offset(x, top), Offset(x + dir * b, top), t)
                        drawLine(ScannerRed, Offset(x, bottom), Offset(x + dir * b, bottom), t)
                    }
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(voiceTitle, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ScannerRed, fontFamily = Mono, maxLines = 1)
                        Text(
                            statusTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (snap.dtcCount > 0) AlertRed else DashWhite,
                            fontFamily = Mono,
                            maxLines = 1
                        )
                    }
                    WaveStrip(active = snap.speaking, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        // Module 3: Tactical Override Controls
        FilmPanel("TACTICAL OVERRIDE", Modifier.weight(1.1f).fillMaxHeight()) {
            Row(Modifier.fillMaxSize().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TacticalKey("SCAN ECU", Modifier.weight(1f), onDiagnostics)
                TacticalKey("PURSUIT", Modifier.weight(1f), onPursuit)
                TacticalKey("COMMS", Modifier.weight(1f), onAssistant)
            }
        }
    }
}

@Composable
private fun TerminatorMiniGauge(label: String, value: String, fraction: Float, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ScannerRed.copy(alpha = 0.85f), fontFamily = Mono, maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DashWhite, fontFamily = Mono, maxLines = 1)
        LedBarGauge(fraction, Modifier.fillMaxWidth().height(7.dp), segments = 6, color = ScannerRed)
    }
}

@Composable
private fun TacticalKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(ScannerRed.copy(alpha = 0.14f))
            .border(1.5.dp, ScannerRed, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = DashWhite,
            fontFamily = Mono,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------- The Matrix

@Composable
private fun MatrixTop(snap: LiveTelemetrySnapshot, edition: FilmEdition?) {
    val loc = Locale.US
    val sigTitle = when (edition) {
        FilmEdition.RealWorld -> "EM CARRIER SCANNER // ${if (snap.linked) "%.1f HZ".format(loc, snap.hz) else "NO CARRIER"}"
        FilmEdition.Zion -> "ZION MAINFRAME CARRIER // ${if (snap.linked) "%.1f HZ".format(loc, snap.hz) else "OFFLINE"}"
        else -> "SIGNAL SCANNER // ${if (snap.linked) "LOCKED %.1f HZ".format(loc, snap.hz) else "SEARCHING"}"
    }
    val glyphStream = remember(snap) { FilmHudText.liveMatrixGlyphStream(snap) }
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilmPanel(sigTitle, Modifier.weight(1f).fillMaxHeight()) {
            WaveStrip(active = snap.linked || snap.speaking, Modifier.fillMaxSize())
        }
        FilmPanel("GLYPH WAVE DECODER // LIVE TELEMETRY HEX", Modifier.weight(1.25f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Text(
                    glyphStream,
                    style = MaterialTheme.typography.labelSmall,
                    color = ScannerRed,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    "VEC: ${snap.rpm ?: "--"} RPM | ${snap.speed ?: "--"} ${snap.speedUnit} | ECT: ${snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "--"} | TPS: ${snap.throttlePct?.let { "$it%" } ?: "--"} | BUS: ${snap.volts?.let { "%.1fV".format(loc, it) } ?: "--"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = DashWhite,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun Vt220(snap: LiveTelemetrySnapshot, edition: FilmEdition?) {
    val lines = remember(snap, edition) { FilmHudText.liveMatrixConsole(snap, edition) }
    Column(Modifier.fillMaxSize().clipToBounds(), verticalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            lines.forEachIndexed { i, line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = when {
                        i == 0 -> DashWhite
                        line.contains("ALERT") || line.contains("LOST") -> AlertRed
                        else -> ScannerRed
                    },
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(ScannerRed.copy(alpha = 0.12f))
                .border(1.dp, ScannerRed.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("RPM:${snap.rpm ?: "--"}", style = MaterialTheme.typography.labelSmall, color = DashWhite, fontFamily = Mono)
            Text("LD:${snap.loadPct?.let { "$it%" } ?: "--"}", style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono)
            Text("FL:${snap.fuelPct?.let { "$it%" } ?: "--"}", style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono)
        }
    }
}

@Composable
private fun MatrixBottom(
    snap: LiveTelemetrySnapshot,
    edition: FilmEdition?,
    onAssistant: () -> Unit,
    onDiagnostics: () -> Unit,
    onPursuit: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val loc = Locale.US
    val empTitle = when (edition) {
        FilmEdition.RealWorld -> "EMP CHARGE // ${snap.volts?.let { "%.1fV".format(loc, it) } ?: "--V"} (ECT ${snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "--"})"
        FilmEdition.Zion -> "APU CAPACITOR // ${snap.volts?.let { "%.1fV".format(loc, it) } ?: "--V"} (LOAD ${snap.loadPct?.let { "$it%" } ?: "--"})"
        else -> "EMP CHARGE // ${snap.volts?.let { "%.1fV".format(loc, it) } ?: "--V"}"
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilmPanel("OPERATOR HARDLINE PATCH", Modifier.weight(1.45f).fillMaxHeight()) {
            Row(Modifier.fillMaxSize().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Hardline("TEL", Modifier.weight(1f)) { open(context, Intent.ACTION_DIAL) }
                Hardline("BT", Modifier.weight(1f)) { open(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
                Hardline("NET", Modifier.weight(1f)) { open(context, Settings.ACTION_WIFI_SETTINGS) }
                Hardline("ECU", Modifier.weight(1f), onDiagnostics)
                Hardline("PURSUIT", Modifier.weight(1.3f), onPursuit)
            }
        }
        FilmPanel(empTitle, Modifier.weight(1f).fillMaxHeight().clickable(onClick = onDiagnostics)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LedBarGauge(((snap.volts ?: 11.0) - 11.0).toFloat() / 3.5f, Modifier.fillMaxWidth().height(24.dp), segments = 16, color = ScannerRed)
            }
        }
        FilmPanel("OPERATOR VOICE TRACE // TAP TO TALK", Modifier.weight(1.25f).fillMaxHeight().clickable(onClick = onAssistant)) {
            WaveStrip(active = snap.speaking, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Hardline(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(ScannerRed.copy(alpha = 0.12f))
            .border(1.5.dp, ScannerRed, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ScannerRed, fontFamily = Mono, maxLines = 1)
    }
}

private fun open(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some head-unit ROMs strip individual screens.
    }
}

// ---------------------------------------------------------------- RoboCop

@Composable
private fun CopVisorBar(state: HudState, snap: LiveTelemetrySnapshot, edition: FilmEdition?, modifier: Modifier) {
    val visorTitle = when (edition) {
        FilmEdition.Ed209 -> "OCP ED-209 // URBAN PACIFICATION VISOR"
        FilmEdition.DeltaCity -> "OCP DELTA CITY // EXECUTIVE SECURITY VISOR"
        else -> "OCP METRO WEST // PATROL UNIT 001 VISOR"
    }
    val velText = "VEL: ${snap.speed ?: "--"} ${snap.speedUnit}" + (snap.speedLimit?.let { " (LIM $it)" } ?: "")
    val rpmText = "RPM: ${snap.rpm?.let { "%,d".format(Locale.US, it) } ?: "--"} // ${if (snap.linked) "%.1f HZ".format(Locale.US, snap.hz) else "OBD OFFLINE"}"
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            val speed = ((state.speed?.toFloatOrNull() ?: 0f) / 120f).coerceIn(0f, 1f)
            val barW = size.width * 0.42f
            drawRoundRect(ScannerRed.copy(alpha = 0.15f), Offset(0f, h * 0.25f), Size(size.width, h * 0.5f), CornerRadius(h * 0.25f))
            drawRoundRect(
                Brush.horizontalGradient(listOf(ScannerRed.copy(alpha = 0.25f), ScannerRed)),
                Offset(0f, h * 0.25f), Size(barW * speed + barW * 0.05f, h * 0.5f), CornerRadius(h * 0.25f)
            )
            val c = Offset(size.width * 0.5f, h / 2f)
            drawCircle(ScannerRed, h * 0.32f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(ScannerRed, h * 0.12f, c, style = Stroke(1.5.dp.toPx()))
            drawLine(ScannerRed, Offset(c.x - h * 0.5f, c.y), Offset(c.x + h * 0.5f, c.y), 1.dp.toPx())
            var x = c.x + h * 0.7f
            var tickIdx = 0
            while (x < size.width - 8.dp.toPx()) {
                val long = tickIdx % 4 == 0
                drawLine(ScannerRed.copy(alpha = 0.8f), Offset(x, h * (if (long) 0.3f else 0.4f)), Offset(x, h * (if (long) 0.7f else 0.6f)), 1.5.dp.toPx())
                x += 14.dp.toPx()
                tickIdx++
            }
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(visorTitle, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DashWhite, fontFamily = Mono, maxLines = 1)
            Text(rpmText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DashWhite, fontFamily = Mono, maxLines = 1)
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                velText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (snap.overLimit) AlertRed else ScannerRed,
                fontFamily = Mono,
                maxLines = 1
            )
            Text(
                if (snap.dtcCount > 0) "STATUTE ALERT: ${snap.dtcCount} DTC" else "SECTOR STATUS: NOMINAL",
                style = MaterialTheme.typography.labelSmall,
                color = if (snap.dtcCount > 0) AlertRed else ScannerRed,
                fontFamily = Mono,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Directives(snap: LiveTelemetrySnapshot, edition: FilmEdition?) {
    val directives = remember(edition) { FilmHudText.liveRobocopDirectives(edition) }
    val telemetry = remember(snap, edition) { FilmHudText.liveRobocopTelemetry(snap, edition) }
    Column(Modifier.fillMaxSize().clipToBounds(), verticalArrangement = Arrangement.SpaceBetween) {
        Text(
            when (edition) {
                FilmEdition.Ed209 -> "ED-209 PACIFICATION DIRECTIVES"
                FilmEdition.DeltaCity -> "DELTA CITY EXEC DIRECTIVES"
                else -> "OCP PRIME DIRECTIVES"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = DashYellow,
            fontFamily = Mono,
            maxLines = 1
        )
        directives.forEach { (n, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(6.dp).height(22.dp)) {
                    drawLine(ScannerRed, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
                    drawLine(ScannerRed, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
                }
                Column(Modifier.padding(start = 5.dp)) {
                    Text(
                        "DIR $n: $text",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScannerRed,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ScannerRed.copy(alpha = 0.45f)))
        Text(
            "PATROL TELEMETRY // LIVE",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = DashYellow,
            fontFamily = Mono,
            maxLines = 1
        )
        telemetry.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, style = MaterialTheme.typography.labelSmall, color = DashWhite, fontFamily = Mono, maxLines = 1)
                Text(
                    v,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (k == "VIOLATIONS" && snap.dtcCount > 0) AlertRed else ScannerRed,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CopDispatch(
    snap: LiveTelemetrySnapshot,
    edition: FilmEdition?,
    onAssistant: () -> Unit,
    onDiagnostics: () -> Unit,
    onPursuit: () -> Unit,
    modifier: Modifier
) {
    val loc = Locale.US
    val roleLeft = when (edition) {
        FilmEdition.Ed209 -> "ED-209" to "DROID"
        FilmEdition.DeltaCity -> "OCP" to "EXEC"
        else -> "CYBORG" to "OFFICER"
    }
    val dispatchTitle = when (edition) {
        FilmEdition.Ed209 -> "OCP DEFENSE SYSTEMS // PACIFICATION AUDIO"
        FilmEdition.DeltaCity -> "OCP TOWER // EXECUTIVE COMMS"
        else -> "OCP DETROIT POLICE DISPATCH // TAP TO TALK"
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilmPanel("", Modifier.weight(2.1f).fillMaxHeight().clickable(onClick = onAssistant)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(roleLeft.first, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ScannerRed, fontFamily = Mono)
                    Text(roleLeft.second, style = MaterialTheme.typography.labelSmall, color = DashWhite, fontFamily = Mono)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dispatchTitle, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono, maxLines = 1)
                        Text(
                            if (snap.linked) "CH/10 // %.1fHZ".format(loc, snap.hz) else "CH/10 // STBY",
                            style = MaterialTheme.typography.labelSmall,
                            color = DashWhite,
                            fontFamily = Mono,
                            maxLines = 1
                        )
                    }
                    WaveStrip(active = snap.speaking, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        FilmPanel("UNIT VITALS", Modifier.weight(1.1f).fillMaxHeight().clickable(onClick = onDiagnostics)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ECT: ${snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "--"}", style = MaterialTheme.typography.labelSmall, color = DashWhite, fontFamily = Mono)
                    Text("BUS: ${snap.volts?.let { "%.1fV".format(loc, it) } ?: "--"}", style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LOAD: ${snap.loadPct?.let { "$it%" } ?: "--"}", style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono)
                    Text("FUEL: ${snap.fuelPct?.let { "$it%" } ?: "--"}", style = MaterialTheme.typography.labelSmall, color = DashWhite, fontFamily = Mono)
                }
            }
        }
        FilmPanel("OCP TACTICAL", Modifier.weight(1.1f).fillMaxHeight()) {
            Row(Modifier.fillMaxSize().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TacticalKey("DIAG", Modifier.weight(1f), onDiagnostics)
                TacticalKey("PURSUIT", Modifier.weight(1f), onPursuit)
                TacticalKey("DISPATCH", Modifier.weight(1f), onAssistant)
            }
        }
    }
}

// ---------------------------------------------------------------- Shared

/** Tactical corner brackets over a map. */
@Composable
private fun TacticalBrackets() {
    Canvas(Modifier.fillMaxSize()) {
        val b = 18.dp.toPx()
        val t = 2.dp.toPx()
        for ((x, y, dx, dy) in listOf(listOf(4f, 4f, 1f, 1f), listOf(size.width - 4f, 4f, -1f, 1f), listOf(4f, size.height - 4f, 1f, -1f), listOf(size.width - 4f, size.height - 4f, -1f, -1f))) {
            drawLine(ScannerRed, Offset(x, y), Offset(x + dx * b, y), t)
            drawLine(ScannerRed, Offset(x, y), Offset(x, y + dy * b), t)
        }
    }
}

/** A horizontal speech-style waveform: animated while [active] (not in lite mode), a quiet static trace otherwise. */
@Composable
private fun WaveStrip(active: Boolean, modifier: Modifier) {
    val phase = if (active && !LocalLiteMode.current) {
        rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "wavePhase")
    } else null
    Canvas(modifier) {
        val bars = 90
        val step = size.width / bars
        val mid = size.height / 2f
        val p = (phase?.value ?: 0f) * 2f * PI.toFloat()
        val amp = if (active) 0.9f else 0.35f
        for (i in 0 until bars) {
            val env = sin(PI * (i + 0.5f) / bars).toFloat()
            val wob = (abs(sin(i * 0.8f + p)) + abs(sin(i * 0.31f - p))) / 2f
            val h = mid * amp * (0.2f + 0.8f * env) * (0.25f + 0.75f * wob)
            drawLine(ScannerRed, Offset(step * (i + 0.5f), mid - h), Offset(step * (i + 0.5f), mid + h), 1.4.dp.toPx(), StrokeCap.Round)
        }
    }
}
