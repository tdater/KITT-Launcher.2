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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.obd.PidCatalog
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.components.CockpitSkin
import com.aiconsole.kittlauncher2.ui.components.LedBarGauge
import com.aiconsole.kittlauncher2.ui.components.skinFrame
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.DashYellow
import com.aiconsole.kittlauncher2.ui.theme.FilmHudText
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.LocalLiteMode
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val Mono = FontFamily.Monospace

/**
 * The landscape Drive screen for the three film themes, built to the designs: a strip across the
 * top, three titled panels (the film's own readout, the map, the vehicle diagnostics) and a strip
 * along the bottom. Everything shown is live data from the same sources as the KITT layout; the
 * map is the same map (with its search, stream and route features) in the centre panel.
 */
@Composable
fun FilmDrivePage(
    skin: CockpitSkin,
    map: @Composable (Modifier) -> Unit,
    hud: () -> HudState,
    reading: (String) -> PidReading?,
    metric: Boolean,
    kittSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val kitt = LocalContext.current.applicationContext as com.aiconsole.kittlauncher2.KittApplication
    androidx.compose.runtime.DisposableEffect(Unit) {
        kitt.obd.watch("film_drive", setOf("gm_tpms_lf", "gm_tpms_rf", "gm_tpms_lr", "gm_tpms_rr", PidCatalog.COOLANT))
        onDispose { kitt.obd.unwatch("film_drive") }
    }
    val state = hud()
    fun v(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 10_000 }?.value
    fun temp(c: Double) = if (metric) "${c.roundToInt()}°C" else "${(c * 9 / 5 + 32).roundToInt()}°F"
    val coolant = v(PidCatalog.COOLANT)?.let(::temp) ?: "--"
    val volts = v(PidCatalog.MODULE_VOLTAGE)
    val fuel = v(PidCatalog.FUEL_LEVEL)
    val load = v(PidCatalog.ENGINE_LOAD)
    val speedText = state.speed ?: "--"
    val rpmText = state.rpmText ?: "--"

    if (skin == CockpitSkin.Terminator) {
        androidx.compose.runtime.DisposableEffect(Unit) {
            com.aiconsole.kittlauncher2.ui.theme.FilmHudState.driveShowsCode = true
            onDispose { com.aiconsole.kittlauncher2.ui.theme.FilmHudState.driveShowsCode = false }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (skin) {
            CockpitSkin.Terminator -> TerminatorTop(state)
            CockpitSkin.Matrix -> MatrixTop(kittSpeaking)
            CockpitSkin.RoboCop -> CopVisorBar(state, Modifier.fillMaxWidth().height(54.dp))
            else -> Unit
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilmPanel(
                when (skin) { CockpitSkin.Matrix -> "VT220 OPERATOR TERMINAL"; CockpitSkin.RoboCop -> ""; else -> "SYSTEM LOG" },
                Modifier.weight(0.70f).fillMaxHeight()
            ) {
                when (skin) {
                    CockpitSkin.Terminator -> MachineCode()
                    CockpitSkin.Matrix -> Vt220(state.linked, state.speedLimit != null)
                    else -> Directives()
                }
            }
            FilmPanel(
                when (skin) {
                    CockpitSkin.Terminator -> "MILITARY WIREFRAME TARGETING MAP"
                    CockpitSkin.Matrix -> "VECTOR RADAR SONAR NIGHT MAP"
                    else -> "OCP METRO WEST TACTICAL MAP"
                },
                Modifier.weight(1.25f).fillMaxHeight()
            ) {
                map(Modifier.fillMaxSize())
                TacticalBrackets()
            }
            FilmPanel(
                when (skin) { CockpitSkin.Terminator -> "T-800 CHASSIS DIAGNOSTIC SCANNER"; CockpitSkin.Matrix -> "CONSTRUCT VEHICLE SCAN"; else -> "" },
                Modifier.weight(1.35f).fillMaxHeight(),
                hazard = skin == CockpitSkin.RoboCop
            ) {
                // The same live hologram vehicle as every other Drive page: tire pressures, coolant, battery, fuel, engine, speed.
                com.aiconsole.kittlauncher2.ui.components.VehicleStatusHud(reading, metric, { state.rpmFraction }, Modifier.fillMaxSize())
            }
        }
        when (skin) {
            CockpitSkin.Terminator -> TerminatorBottom(Modifier.fillMaxWidth().height(60.dp))
            CockpitSkin.Matrix -> MatrixBottom(volts, kittSpeaking, Modifier.fillMaxWidth().height(66.dp))
            CockpitSkin.RoboCop -> CopDispatch(kittSpeaking, Modifier.fillMaxWidth().height(66.dp))
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
        Box(modifier.clip(shape).background(Color.Black.copy(alpha = 0.55f)).skinFrame(shape, ScannerRed)) {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono, maxLines = 1)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { content() }
            }
        }
    }
}

// ---------------------------------------------------------------- The Terminator

@Composable
private fun TerminatorTop(state: HudState) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STANDARD SCANNER", style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono)
                LedBarGauge(state.rpmFraction, Modifier.fillMaxWidth().height(22.dp), segments = 20, color = ScannerRed)
            }
            Canvas(Modifier.width(44.dp).height(36.dp)) {
                val c = center
                drawCircle(ScannerRed, 13.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                drawLine(ScannerRed, Offset(c.x - 20.dp.toPx(), c.y), Offset(c.x + 20.dp.toPx(), c.y), 1.5.dp.toPx())
                drawLine(ScannerRed, Offset(c.x, c.y - 17.dp.toPx()), Offset(c.x, c.y + 17.dp.toPx()), 1.5.dp.toPx())
            }
            Column(Modifier.weight(1f)) {
                Text("20-HIGH-TCH TARGETING TRACKER", style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono, modifier = Modifier.align(Alignment.End))
                val speed = (state.speed?.toFloatOrNull() ?: 0f) / 120f
                LedBarGauge(speed, Modifier.fillMaxWidth().height(22.dp), segments = 20, color = ScannerRed)
            }
        }
        Text(
            if (state.linked) "SYSTEM STATUS: OPERATIONAL" else "SYSTEM STATUS: NO VEHICLE LINK",
            style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono
        )
    }
}

@Composable
private fun MachineCode() {
    val lines = FilmHudText.MACHINE_CODE.take(16) + listOf("SCAN.VEHICLE OK", "SCAN.VEHICLE OK", "THREAT ASSESSMENT: NONE")
    Column(Modifier.fillMaxSize().clipToBounds()) {
        lines.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono, maxLines = 1) }
    }
}

/** The top-down chassis (Blender render, tires coloured by pressure, engine glowing by heat) with the three big readouts beside it. */
@Composable
private fun ChassisScan(coolant: String, rpm: String, speed: String, speedUnit: String, tires: List<Color>, engine: Color) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.aiconsole.kittlauncher2.ui.components.ChassisPlan(ScannerRed, tires, engine, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            BigStat("COOLANT", coolant)
            BigStat("RPM", rpm)
            BigStat("SPEED", "$speed\n$speedUnit")
        }
    }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono)
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = ScannerRed, fontFamily = Mono)
    }
}

@Composable
private fun TerminatorBottom(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = 3.dp.toPx()
            val b = 16.dp.toPx()
            val inset = size.width * 0.28f
            val top = 6.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            for ((x, dir) in listOf(inset to 1f, size.width - inset to -1f)) {
                drawLine(ScannerRed, Offset(x, top), Offset(x, bottom), t)
                drawLine(ScannerRed, Offset(x, top), Offset(x + dir * b, top), t)
                drawLine(ScannerRed, Offset(x, bottom), Offset(x + dir * b, bottom), t)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CYBERDYNE STATUS BRACKETS", style = MaterialTheme.typography.titleSmall, color = ScannerRed, fontFamily = Mono)
            Text("MODEL 101 VOICE TERMINAL READOUT", style = MaterialTheme.typography.labelMedium, color = ScannerRed, fontFamily = Mono)
        }
    }
}

// ---------------------------------------------------------------- The Matrix

@Composable
private fun MatrixTop(speaking: Boolean) {
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilmPanel("SIGNAL SCANNER", Modifier.weight(1f).fillMaxHeight()) { WaveStrip(active = true, Modifier.fillMaxSize()) }
        FilmPanel("GLYPH WAVE DECODER", Modifier.weight(1f).fillMaxHeight()) {
            Text(
                "01 61 0001 5000 10 000 | 11001 0000 02 | 0101 000 ｱｲｳｴｵｶｷｸｹｺ 0110 1010 ｻｼｽｾｿ 11 0011 ﾀﾁﾂﾃﾄ 0101",
                style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = Mono, maxLines = 3
            )
        }
    }
}

@Composable
private fun Vt220(linked: Boolean, radar: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            "OPERATOR ONLINE",
            "> SIGNAL: ${if (linked) "CLEAN" else "NO VEHICLE LINK"}",
            "> RADAR: ${if (radar) "LIMIT KNOWN" else "SWEEPING"}",
            "> SYNCED TO MAIN-FRAME.",
            ">_"
        ).forEach { Text(it, style = MaterialTheme.typography.titleSmall, color = ScannerRed, fontFamily = Mono) }
    }
}

@Composable
private fun MatrixDiagnostics(coolant: String, volts: Double?, speed: String, unit: String, rpm: String, fuel: Double?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        listOf(
            "ENGINE COOLANT: $coolant",
            "BATTERY: ${volts?.let { "%.1fV".format(it) } ?: "--"}",
            "SPEED: $speed $unit",
            "RPM: $rpm",
            "FUEL: ${fuel?.let { "${it.roundToInt()}%" } ?: "--"}"
        ).forEach { Text(it, style = MaterialTheme.typography.titleSmall, color = ScannerRed, fontFamily = Mono, maxLines = 1) }
    }
}

@Composable
private fun MatrixBottom(volts: Double?, speaking: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilmPanel("OPERATOR PHONE HARDLINE PATCH", Modifier.weight(1.3f).fillMaxHeight()) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Hardline("TEL", Modifier.weight(1f)) { open(context, Intent.ACTION_DIAL) }
                Hardline("BT", Modifier.weight(1f)) { open(context, Settings.ACTION_BLUETOOTH_SETTINGS) }
                Hardline("NET", Modifier.weight(1f)) { open(context, Settings.ACTION_WIFI_SETTINGS) }
            }
        }
        FilmPanel("EMP CHARGE", Modifier.weight(1f).fillMaxHeight()) {
            LedBarGauge(((volts ?: 11.0) - 11.0).toFloat() / 3.5f, Modifier.fillMaxWidth().height(24.dp), segments = 16, color = ScannerRed)
        }
        FilmPanel("AGENT VOICE WAVEFORM", Modifier.weight(1.3f).fillMaxHeight()) { WaveStrip(active = speaking, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun Hardline(label: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(modifier.fillMaxHeight().clip(shape).border(1.5.dp, ScannerRed, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ScannerRed, fontFamily = Mono)
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
private fun CopVisorBar(state: HudState, modifier: Modifier) {
    Canvas(modifier) {
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
        var tick = 0
        while (x < size.width - 8.dp.toPx()) {
            val long = tick % 4 == 0
            drawLine(ScannerRed.copy(alpha = 0.8f), Offset(x, h * (if (long) 0.3f else 0.4f)), Offset(x, h * (if (long) 0.7f else 0.6f)), 1.5.dp.toPx())
            x += 14.dp.toPx()
            tick++
        }
    }
}

@Composable
private fun Directives() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        listOf("1" to "SERVE PUBLIC TRUST", "2" to "PROTECT THE INNOCENT", "3" to "UPHOLD THE LAW", "4" to "[CLASSIFIED]").forEach { (n, text) ->
            Row {
                Canvas(Modifier.width(8.dp).height(34.dp)) {
                    drawLine(ScannerRed, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
                    drawLine(ScannerRed, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx())
                }
                Column(Modifier.padding(start = 6.dp)) {
                    Text("DIRECTIVE $n:", style = MaterialTheme.typography.labelLarge, color = ScannerRed, maxLines = 1)
                    Text(text, style = MaterialTheme.typography.labelLarge, color = ScannerRed, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PatrolCar(coolant: String, coolantC: Double?, load: Double?, speed: String, unit: String, speedFraction: Float, volts: Double?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
        Text("PATROL CAR 54", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DashWhite, maxLines = 1)
        LedBarGauge(((coolantC ?: 0.0) / 130.0).toFloat(), Modifier.fillMaxWidth().height(8.dp), segments = 20, color = ScannerRed)
        CopLine("COOLANT [$coolant]", "[STATUS: ${if ((coolantC ?: 0.0) < 105.0) "OK" else "HOT"}]")
        CopLine("ENGINE LOAD [${load?.let { "${it.roundToInt()}%" } ?: "--"}]", "[HYDRAULICS: ${if (load != null) "NOMINAL" else "NO DATA"}]")
        Text("SPEED $speed $unit", style = MaterialTheme.typography.labelLarge, color = DashWhite, maxLines = 1)
        LedBarGauge(speedFraction, Modifier.fillMaxWidth().height(8.dp), segments = 20, color = ScannerRed)
        CopLine("ALTERNATOR [${volts?.let { "%.1fV".format(it) } ?: "--"}]", "[${if ((volts ?: 0.0) > 13.2) "CHARGING" else "NOT CHARGING"}]")
    }
}

@Composable
private fun CopLine(a: String, b: String) {
    Column {
        Text(a, style = MaterialTheme.typography.labelMedium, color = DashWhite, maxLines = 1)
        Text(b, style = MaterialTheme.typography.labelSmall, color = ScannerRed, maxLines = 1)
    }
}

@Composable
private fun CopDispatch(speaking: Boolean, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilmPanel("", Modifier.weight(3f).fillMaxHeight()) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CYBORG", style = MaterialTheme.typography.labelSmall, color = ScannerRed)
                    Text("OFFICER", style = MaterialTheme.typography.labelSmall, color = ScannerRed)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("OCP DETROIT POLICE DISPATCH", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = ScannerRed, maxLines = 1)
                        Text("CH/10", style = MaterialTheme.typography.labelSmall, color = ScannerRed)
                    }
                    WaveStrip(active = speaking, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        FilmPanel("", Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("OCP", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = ScannerRed)
                Text("DETROIT POLICE", style = MaterialTheme.typography.labelSmall, color = ScannerRed, maxLines = 1)
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
