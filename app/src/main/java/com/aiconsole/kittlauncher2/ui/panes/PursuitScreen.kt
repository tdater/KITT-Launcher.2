package com.aiconsole.kittlauncher2.ui.panes

import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.PI
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.components.skinFrame
import com.aiconsole.kittlauncher2.ui.components.GForceCircle
import com.aiconsole.kittlauncher2.ui.components.Annunciator
import com.aiconsole.kittlauncher2.KittApplication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import com.aiconsole.kittlauncher2.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.obd.ObdManager
import com.aiconsole.kittlauncher2.obd.PidCatalog
import com.aiconsole.kittlauncher2.obd.PidDefinition
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.LedBarGauge
import com.aiconsole.kittlauncher2.ui.components.VoiceModulator
import com.aiconsole.kittlauncher2.ui.gauges.SegmentDisplay
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import com.aiconsole.kittlauncher2.ui.theme.ThemeState
import com.aiconsole.kittlauncher2.ui.theme.UiTheme
import com.aiconsole.kittlauncher2.ui.theme.HudStyle
import com.aiconsole.kittlauncher2.ui.theme.assistantName
import com.aiconsole.kittlauncher2.ui.theme.hud

/**
 * Pursuit mode: a full-screen dash in the style of KITT's 1982 console. A bar-graph
 * speedometer across the top, the big digital speed, the tach bar, the voice modulator in the
 * middle, and coolant, battery and throttle readouts. Glanceable from a distance.
 */
@Composable
fun PursuitScreen(
    hud: () -> HudState,
    reading: (String) -> PidReading?,
    definition: (String) -> PidDefinition?,
    metric: Boolean,
    kittSpeaking: Boolean,
    onKitt: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (BuildConfig.HOLO_DESIGN) {
        PursuitScreenHolo(hud, reading, definition, metric, kittSpeaking, onKitt, onDismiss, modifier)
        return
    }
    BackHandler(onBack = onDismiss)
    val state = hud()
    val speedValue = state.speed?.toDoubleOrNull() ?: 0.0
    val topSpeed = if (metric) 200.0 else 120.0
    val speedColor = if (state.overLimit) ScannerRed else DashAmber

    fun readout(id: String): String {
        val def = definition(id) ?: return "--"
        val value = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 10_000 }?.value ?: return "--"
        return ObdManager.formatValue(def.displayValue(value, metric), def)
    }
    fun unit(id: String) = definition(id)?.unit(metric).orEmpty()

    BoxWithConstraints(
        modifier
            .background(KittBlack)
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(12.dp)
    ) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PURSUIT MODE", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ScannerRed)
                Text(state.linkLabel, style = MaterialTheme.typography.labelMedium, color = if (state.linked) LedGreen else DashGrey)
                KittButton("Exit", onDismiss, Modifier.height(40.dp).width(96.dp), DashGrey)
            }
            // Bar-graph speedometer, the 1982 way: a long row of lamps.
            LedBarGauge((speedValue / topSpeed).toFloat(), Modifier.fillMaxWidth().height(if (wide) 34.dp else 44.dp), segments = 40, color = speedColor)
            if (wide) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Readouts(::readout, ::unit, Modifier.weight(1f).fillMaxHeight())
                    BigSpeed(state, speedColor, Modifier.weight(1.6f).fillMaxHeight())
                    KittBox(kittSpeaking, onKitt, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                BigSpeed(state, speedColor, Modifier.fillMaxWidth().weight(1.3f))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Readouts(::readout, ::unit, Modifier.weight(1f).fillMaxHeight())
                    KittBox(kittSpeaking, onKitt, Modifier.weight(1f).fillMaxHeight())
                }
            }
            LedBarGauge(state.rpmFraction, Modifier.fillMaxWidth().height(24.dp), segments = 32)
            Text(state.rpmText?.let { "$it RPM" } ?: "RPM", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        }
    }
}

@Composable
private fun BigSpeed(state: HudState, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        SegmentDisplay(state.speed ?: "--", Modifier.fillMaxWidth().weight(1f), color, cells = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.speedUnit, style = MaterialTheme.typography.titleMedium, color = color)
            state.speedLimit?.let { Text("LIMIT $it", style = MaterialTheme.typography.titleMedium, color = if (state.overLimit) ScannerRed else DashGrey) }
        }
    }
}

@Composable
private fun Readouts(value: (String) -> String, unit: (String) -> String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PidCatalog.COOLANT to "COOLANT", PidCatalog.MODULE_VOLTAGE to "BATTERY", PidCatalog.THROTTLE to "THROTTLE").forEach { (id, label) ->
            val shape = MaterialTheme.shapes.small
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(shape)
                    .border(1.dp, KittBezel, shape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("$label ${unit(id)}".trim(), style = MaterialTheme.typography.labelSmall, color = DashGrey)
                SegmentDisplay(value(id), Modifier.fillMaxWidth().weight(1f), DashAmber, cells = 4)
            }
        }
    }
}

@Composable
private fun KittBox(speaking: Boolean, onKitt: () -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    Box(modifier.clip(shape).border(1.dp, ScannerRed.copy(alpha = 0.6f), shape)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VoiceModulator(active = speaking, modifier = Modifier.fillMaxWidth().weight(1f))
            KittButton(assistantName().uppercase(), onKitt, Modifier.fillMaxWidth().height(48.dp), ScannerRed, active = true)
            Text("TAP TO TALK", Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = DashGrey, textAlign = TextAlign.Center)
        }
    }
}

private const val MAP_ID = "01_0B"
private const val BARO_ID = "01_33"
private const val OIL_ID = "01_5C"
private val BoostCyan = Color(0xFF4DD0E1)

/**
 * Holo-edition Super Pursuit mode: KITT's 1982 instrument cluster, laid out like the design. Warning lamps on
 * both shoulders, a labelled RPM arc with a needle, the speed arc with the big red digits inside
 * it, the voice oscilloscope between them (tap it to talk to KITT) and a row of readout cards
 * ending with the G-force circle. Everything is live OBD data; a card shows "--" until the car
 * reports that value.
 */
@Composable
private fun PursuitScreenHolo(
    hud: () -> HudState,
    reading: (String) -> PidReading?,
    definition: (String) -> PidDefinition?,
    metric: Boolean,
    kittSpeaking: Boolean,
    onKitt: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    val app = LocalContext.current.applicationContext as KittApplication
    DisposableEffect(Unit) {
        app.obd.watch("pursuit", setOf(MAP_ID, BARO_ID, OIL_ID, PidCatalog.FUEL_LEVEL, PidCatalog.COOLANT, PidCatalog.MODULE_VOLTAGE, PidCatalog.THROTTLE))
        onDispose { app.obd.unwatch("pursuit") }
    }
    val state = hud()
    val speedValue = state.speed?.toDoubleOrNull() ?: 0.0
    val topSpeed = if (metric) 200.0 else 120.0
    val speedColor = if (state.overLimit) AlertRed else ScannerRed

    fun fresh(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 10_000 }?.value
    fun temp(c: Double) = if (metric) c.roundToInt() else (c * 9 / 5 + 32).roundToInt()
    val throttle = fresh(PidCatalog.THROTTLE) ?: 0.0
    val boostKpa = fresh(MAP_ID)?.let { it - (fresh(BARO_ID) ?: 101.3) }
    val coolantC = fresh(PidCatalog.COOLANT)
    val oilC = fresh(OIL_ID)
    val fuel = fresh(PidCatalog.FUEL_LEVEL)
    val volts = fresh(PidCatalog.MODULE_VOLTAGE)
    val tempUnit = if (metric) "°C" else "°F"

    val left = listOf(
        Annunciator("NORM", state.linked && throttle < 60, DashAmber),
        Annunciator("PURSUIT", throttle >= 60, ScannerRed),
        Annunciator("", false, LedGreen),
        Annunciator("", false, DashAmber),
        Annunciator("", false, ScannerRed),
        Annunciator("AUTO CRUISE", speedValue > 20 && throttle in 1.0..35.0, LedGreen)
    )
    val right = listOf(
        Annunciator("RADAR", state.speedLimit != null, ScannerRed),
        Annunciator("EJECT", false, ScannerRed),
        Annunciator("SCAN", state.linked, DashAmber),
        Annunciator("", false, ScannerRed),
        Annunciator("SCAN", state.linked, DashAmber),
        Annunciator("BOOST", (boostKpa ?: 0.0) > 5.0, ScannerRed)
    )
    val cards = listOf(
        CardSpec("TURBO BOOST", boostKpa?.let { if (metric) "%.2f".format(it / 100) else "%.1f".format(it * 0.145038) } ?: "--", if (metric) "BAR" else "PSI", BoostCyan, null),
        CardSpec("OIL TEMP", oilC?.let { temp(it).toString() } ?: "--", tempUnit, LedGreen, null),
        CardSpec("COOLANT", coolantC?.let { temp(it).toString() } ?: "--", tempUnit, LedGreen, null),
        CardSpec("FUEL LEVEL", fuel?.let { it.roundToInt().toString() } ?: "--", "%", DashAmber, ((fuel ?: 0.0) / 100.0).toFloat()),
        CardSpec("BATTERY", volts?.let { "%.1f".format(it) } ?: "--", "V", LedGreen, null)
    )

    BoxWithConstraints(
        modifier
            .background(KittBlack)
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(10.dp)
    ) {
        val wide = maxWidth > maxHeight
        val tiles = @Composable { list: List<Annunciator>, m: Modifier ->
            Column(m, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                list.chunked(3).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { Tile(it, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        }
        val rpmArc = @Composable { m: Modifier ->
            Box(m, contentAlignment = Alignment.Center) {
                ArcGauge(state.rpmFraction, Modifier.fillMaxSize(), DashAmber, listOf("0", "1000", "2000", "3000", "4000", "5000", "6000", "7000"), needle = true)
                Column(Modifier.padding(top = 78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RPM", style = MaterialTheme.typography.titleMedium, color = DashAmber)
                    Text(state.rpmText ?: "--", style = MaterialTheme.typography.labelMedium, color = DashGrey)
                }
            }
        }
        val speedArc = @Composable { m: Modifier ->
            Box(m, contentAlignment = Alignment.Center) {
                ArcGauge((speedValue / topSpeed).toFloat(), Modifier.fillMaxSize(), speedColor, emptyList(), needle = false)
                Column(Modifier.fillMaxWidth(0.5f).fillMaxHeight(0.6f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val pursuitTitle = when (ThemeState.theme.hud) {
                        HudStyle.Terminator -> "CYBERDYNE INTERCEPT"
                        HudStyle.Matrix -> "OVERDRIVE CONSTRUCT"
                        HudStyle.Future -> "88 MPH TIME RUN"
                        HudStyle.RoboCop -> "OCP HOT PURSUIT"
                        null -> if (ThemeState.theme == UiTheme.Karr) "KARR PURSUIT" else "SUPER PURSUIT"
                    }
                    Text(
                        pursuitTitle,
                        style = MaterialTheme.typography.labelSmall.copy(shadow = Shadow(speedColor, Offset.Zero, 10f)),
                        color = speedColor,
                        maxLines = 1
                    )
                    SegmentDisplay(state.speed ?: "--", Modifier.fillMaxWidth().weight(1f), speedColor, cells = 3)
                    Text(state.speedUnit, style = MaterialTheme.typography.titleSmall, color = speedColor)
                }
            }
        }
        val scope = @Composable { m: Modifier ->
            Column(m.clickable(onClick = onKitt), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("VOICE/AUDIO", style = MaterialTheme.typography.labelSmall, color = DashWhite, maxLines = 1)
                VoiceModulator(active = kittSpeaking, modifier = Modifier.fillMaxWidth().weight(1f))
                Text("VOICE OSCILLOSCOPE", style = MaterialTheme.typography.labelSmall, color = DashWhite, maxLines = 1)
            }
        }
        val cardRow = @Composable { m: Modifier ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cards.forEach { Card(it, Modifier.weight(1f).fillMaxHeight()) }
                Box(Modifier.weight(0.8f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GForceCircle(Modifier.weight(1f).fillMaxWidth())
                        Text("G-FORCE", style = MaterialTheme.typography.labelSmall, color = DashWhite)
                    }
                }
                KittButton("Exit", onDismiss, Modifier.weight(0.6f).fillMaxHeight(), DashGrey)
            }
        }

        if (wide) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().weight(0.55f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles(left, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        LedBarGauge(state.rpmFraction, Modifier.fillMaxWidth().height(34.dp), segments = 26, color = ScannerRed)
                    }
                    tiles(right, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().weight(2.4f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rpmArc(Modifier.weight(1.25f).fillMaxHeight())
                    scope(Modifier.weight(0.7f).fillMaxHeight())
                    speedArc(Modifier.weight(1.25f).fillMaxHeight())
                }
                cardRow(Modifier.fillMaxWidth().weight(0.95f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles(left, Modifier.weight(1f).fillMaxHeight())
                    tiles(right, Modifier.weight(1f).fillMaxHeight())
                }
                LedBarGauge(state.rpmFraction, Modifier.fillMaxWidth().height(24.dp), segments = 26, color = ScannerRed)
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rpmArc(Modifier.weight(1f).fillMaxHeight())
                    speedArc(Modifier.weight(1f).fillMaxHeight())
                }
                scope(Modifier.fillMaxWidth().height(90.dp))
                cardRow(Modifier.fillMaxWidth().height(86.dp))
            }
        }
    }
}

private class CardSpec(val title: String, val value: String, val unit: String, val color: Color, val bar: Float?)

@Composable
private fun Tile(a: Annunciator, modifier: Modifier) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(
        modifier
            .clip(shape)
            .background(if (a.lit) a.color.copy(alpha = 0.18f) else KittBlack)
            .skinFrame(shape, if (a.lit) a.color else a.color.copy(alpha = 0.35f), selected = a.lit),
        contentAlignment = Alignment.Center
    ) {
        Text(
            a.label,
            style = MaterialTheme.typography.labelSmall.copy(shadow = if (a.lit) Shadow(a.color, Offset.Zero, 10f) else null),
            color = if (a.lit) a.color else a.color.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun Card(spec: CardSpec, modifier: Modifier) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier.clip(shape).background(KittBlack).skinFrame(shape, spec.color).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(spec.title, style = MaterialTheme.typography.labelSmall, color = spec.color, maxLines = 1)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SegmentDisplay(spec.value, Modifier.weight(1f).fillMaxHeight(), spec.color, cells = 4)
            Text(spec.unit, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
        }
        spec.bar?.let { LedBarGauge(it, Modifier.fillMaxWidth().height(8.dp), segments = 8, color = spec.color) }
    }
}

/**
 * A 240° arc of lamps that fill with [level], graded toward red at the top end, with optional
 * numbered tick labels and a needle.
 */
@Composable
private fun ArcGauge(level: Float, modifier: Modifier, color: Color, labels: List<String>, needle: Boolean, segments: Int = 40) {
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val stroke = side * 0.075f
        val radius = side / 2f - stroke
        val center = Offset(size.width / 2f, size.height / 2f + radius * 0.12f)
        val start = 150f
        val sweep = 240f
        val lit = (level.coerceIn(0f, 1f) * segments).roundToInt()
        for (i in 0 until segments) {
            val fraction = (i + 1f) / segments
            val on = if (fraction > 0.82f) lerpToRed(color, (fraction - 0.82f) / 0.18f) else color
            drawArc(
                color = if (i < lit) on else on.copy(alpha = 0.13f),
                startAngle = start + i * sweep / segments + 0.4f,
                sweepAngle = sweep / segments - 0.8f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(stroke)
            )
        }
        if (labels.isNotEmpty()) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = side * 0.05f
                textAlign = android.graphics.Paint.Align.CENTER
                this.color = DashWhite.copy(alpha = 0.85f).toArgb()
            }
            drawIntoCanvas { c ->
                labels.forEachIndexed { k, text ->
                    val a = Math.toRadians((start + sweep * k / (labels.size - 1)).toDouble())
                    val r = radius - stroke * 1.25f
                    c.nativeCanvas.drawText(text, center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat() + paint.textSize / 3f, paint)
                }
            }
        }
        if (needle) {
            val a = Math.toRadians((start + sweep * level.coerceIn(0f, 1f)).toDouble())
            val tip = Offset(center.x + (cos(a) * radius * 0.82f).toFloat(), center.y + (sin(a) * radius * 0.82f).toFloat())
            drawLine(color.copy(alpha = 0.35f), center, tip, 8.dp.toPx())
            drawLine(DashWhite, center, tip, 2.5.dp.toPx())
            drawCircle(color, 6.dp.toPx(), center)
        }
    }
}

private fun lerpToRed(from: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(from, AlertRed, t.coerceIn(0f, 1f))
