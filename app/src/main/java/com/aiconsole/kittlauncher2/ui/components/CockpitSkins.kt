package com.aiconsole.kittlauncher2.ui.components

import kotlin.math.hypot
import com.aiconsole.kittlauncher2.R
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.os.SystemClock
import com.aiconsole.kittlauncher2.ui.theme.LocalLiteMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.abs
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Brush
import com.aiconsole.kittlauncher2.ui.gauges.SegmentDisplay
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.obd.PidCatalog
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.DashYellow
import com.aiconsole.kittlauncher2.ui.theme.HudStyle
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import com.aiconsole.kittlauncher2.ui.theme.ThemeState
import com.aiconsole.kittlauncher2.ui.theme.hud
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One cockpit look per film. Colors already follow the theme palette; the skin adds each film's
 * own vocabulary, vehicle outline and framing so the Holo-edition screens are not palette swaps.
 */
enum class CockpitSkin(
    val hudTitle: String,
    val tireLabel: String,
    val coolantLabel: String,
    val batteryLabel: String,
    val tachLabel: String,
    val queueTitle: String,
    val arcadeTab: String,
    val streamTab: String
) {
    Kitt("Vehicle Status", "TPMS", "COOLANT", "BATTERY", "TACHOMETER", "PLAYLIST / KITT", "Arcade games", "Streaming TV"),
    Terminator("T-800 Chassis Scan", "ACTR", "THERMAL", "CELL", "CELL OUTPUT", "TARGET QUEUE", "Arcade targets", "Video uplink"),
    Matrix("Construct Status", "NODE", "HEAT", "POWER", "SIGNAL", "OPERATOR LOG", "Simulation", "Broadcast"),
    Future("Time Machine Status", "FLUX", "COOLANT", "PLUTONIUM", "GIGAWATTS", "TIMELINE", "1980s arcade", "Video broadcast"),
    RoboCop("Unit Diagnostics", "STRUT", "COOLANT", "ALTERNATOR", "HYDRAULICS", "DISPATCH QUEUE", "Arcade games", "Dispatch video");

    companion object {
        fun current(): CockpitSkin = when (ThemeState.theme.hud) {
            null -> Kitt
            HudStyle.Terminator -> Terminator
            HudStyle.Matrix -> Matrix
            HudStyle.Future -> Future
            HudStyle.RoboCop -> RoboCop
        }
    }
}

private const val KPA_TO_PSI = 0.145038
private val TireIds = listOf("gm_tpms_lf", "gm_tpms_rf", "gm_tpms_lr", "gm_tpms_rr")

/**
 * Vehicle status HUD in the mockups' style: a side-view wireframe with a colored tire at each
 * axle, tire readouts, coolant, battery and a tach bar. Live OBD values only: a tire shows "--"
 * until the car reports it (tire PIDs are manufacturer-specific, currently GM). Tires are green
 * in range, red when low or high, and amber while unknown.
 */
@Composable
fun VehicleStatusHud(
    reading: (String) -> PidReading?,
    metric: Boolean,
    rpmFraction: () -> Float,
    modifier: Modifier = Modifier
) {
    val skin = CockpitSkin.current()
    val obd = (LocalContext.current.applicationContext as KittApplication).obd
    DisposableEffect(Unit) {
        obd.watch("vehicle_hud", TireIds.toSet() + PidCatalog.COOLANT + PidCatalog.MODULE_VOLTAGE + PidCatalog.SPEED + PidCatalog.THROTTLE + PidCatalog.ENGINE_LOAD + PidCatalog.FUEL_LEVEL + CarReadouts.PIDS)
        onDispose { obd.unwatch("vehicle_hud") }
    }
    fun fresh(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 15_000 }?.value
    fun tire(id: String): String {
        val kpa = fresh(id) ?: return "--"
        return if (metric) "${kpa.roundToInt()} kPa" else "${(kpa * KPA_TO_PSI).roundToInt()} PSI"
    }
    fun tireColor(id: String): Color {
        val psi = (fresh(id) ?: return DashAmber) * KPA_TO_PSI
        return if (psi in 26.0..40.0) LedGreen else AlertRed
    }
    val coolant = fresh(PidCatalog.COOLANT)?.let { if (metric) "${it.roundToInt()}°C" else "${(it * 9 / 5 + 32).roundToInt()}°F" } ?: "--"
    val volts = fresh(PidCatalog.MODULE_VOLTAGE)?.let { "%.1fV".format(it) } ?: "--"

    val lite = LocalLiteMode.current
    val context = LocalContext.current
    val dtcs by obd.dtcs.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    val stats = remember { CarReadouts.ALL.associate { it.key to ReadoutStats() } }
    val readingNow = rememberUpdatedState(reading)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            for (r in CarReadouts.ALL) {
                readingNow.value(r.pid)?.takeIf { System.currentTimeMillis() - it.timestamp < 15_000 }?.let { stats.getValue(r.key).add(it.value) }
            }
        }
    }
    val spin = remember { mutableFloatStateOf(0f) }
    val pulse = remember { mutableFloatStateOf(0f) }
    val turn = remember { mutableFloatStateOf(HOLO_REST_YAW) }
    val scanPhase = remember { mutableFloatStateOf(0f) }
    val speedNow = rememberUpdatedState(fresh(PidCatalog.SPEED) ?: 0.0)
    // The spokes and the engine glow only move while the car does; in lite mode nothing animates.
    LaunchedEffect(lite) {
        if (lite) return@LaunchedEffect
        // The hologram turns slowly on its base, blending between neighbouring frames; the scan band sweeps up it.
        var last = SystemClock.uptimeMillis()
        while (true) {
            delay(33)
            val now = SystemClock.uptimeMillis()
            val elapsed = now - last
            last = now
            turn.floatValue = Turntable.advance(turn.floatValue, elapsed)
            scanPhase.floatValue = (scanPhase.floatValue + 0.24f * elapsed.coerceAtMost(250L) / 1000f) % 1f
        }
    }
    LaunchedEffect(lite) {
        if (lite) return@LaunchedEffect
        while (true) {
            delay(66)
            if (speedNow.value > 3.0) spin.floatValue = (spin.floatValue + (speedNow.value / 10.0 * 0.25).coerceAtMost(8.0).toFloat() * 0.066f) % (2f * PI.toFloat())
            if (rpmFraction() > 0.01f) pulse.floatValue = (pulse.floatValue + (0.5f + rpmFraction() * 3f) * 0.066f) % 1f
        }
    }
    val milOn = dtcs?.all?.isNotEmpty() == true
    val book = remember(skin) { readFlipbook(context, skin) }
    // Decode every frame off the UI thread before the turntable reaches it (lite mode keeps one view).
    val ready = remember(book) { mutableIntStateOf(0) }
    LaunchedEffect(book, lite) {
        if (lite) return@LaunchedEffect
        val start = Turntable.blend(turn.floatValue, book.frames.size).from
        for (i in Turntable.preloadOrder(start, book.frames.size)) {
            val image = withContext(Dispatchers.IO) { HoloFrames.load(context, book, i) }
            HoloFrames.put(book, i, image)
            ready.intValue++
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
    // A short panel drops the tach bar so the car keeps its height and the tags stay apart.
    val compact = maxHeight < 170.dp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Readout("${skin.tireLabel} FRONT R", tire(TireIds[1]), tireColor(TireIds[1]))
            Readout("${skin.tireLabel} REAR R", tire(TireIds[3]), tireColor(TireIds[3]), alignEnd = true)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Canvas(Modifier.fillMaxSize()) {
            // The animation clocks are read here, in the draw pass, so only this canvas redraws as they tick.
            val moving = !lite && (speedNow.value > 3.0)
            val live = LiveCar(
                coolantC = fresh(PidCatalog.COOLANT), rpm = rpmFraction(), speedKmh = fresh(PidCatalog.SPEED), throttle = fresh(PidCatalog.THROTTLE),
                load = fresh(PidCatalog.ENGINE_LOAD), fuel = fresh(PidCatalog.FUEL_LEVEL), volts = fresh(PidCatalog.MODULE_VOLTAGE),
                mil = milOn, metric = metric, spin = if (moving) spin.floatValue else null, pulse = if (lite) 0f else pulse.floatValue
            )
            val yaw = if (lite) HOLO_REST_YAW else turn.floatValue
            ready.intValue // redraw as frames arrive
            val blend = Turntable.blend(yaw, book.frames.size)
            val first = HoloFrames.get(book, blend.from)
            val second = HoloFrames.get(book, blend.to)
            val image = first ?: holoBitmap(context, book, blend.from)
            // Until both neighbours are decoded (and always in lite mode) show a single frame.
            val mix = if (!lite && first != null && second != null) blend.mix else 0f
            val frame = if (mix > 0f) book.frames[blend.from].mixWith(book.frames[blend.to], mix) else book.frames[blend.from]
            drawHologram(skin, book, image, second, mix, frame, ScannerRed, tireColor(TireIds[0]), tireColor(TireIds[1]), tireColor(TireIds[2]), tireColor(TireIds[3]), rpmFraction(), live, scanPhase.floatValue)
        }
        selected?.let { key ->
            CarReadouts.byKey(key)?.let { spec ->
                ReadoutCard(spec, fresh(spec.pid), stats.getValue(key), metric, { selected = null }, Modifier.align(Alignment.Center))
            }
        }
        }
        Row(Modifier.fillMaxWidth().height(if (compact) 40.dp else 46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in CarReadouts.ALL) {
                ReadoutChip(CarReadouts.chipLabel(r, metric), CarReadouts.chipValue(r.key, fresh(r.pid), metric), CarReadouts.level(r.key, fresh(r.pid)), selected == r.key,
                    { selected = if (selected == r.key) null else r.key }, Modifier.weight(1f).fillMaxHeight())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Readout("${skin.tireLabel} FRONT L", tire(TireIds[0]), tireColor(TireIds[0]))
            Readout(skin.coolantLabel, coolant, if (coolant == "--") DashGrey else LedGreen)
            Readout(skin.batteryLabel, volts, if (volts == "--") DashGrey else LedGreen)
            Readout("${skin.tireLabel} REAR L", tire(TireIds[2]), tireColor(TireIds[2]), alignEnd = true)
        }
        if (!compact) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(skin.tachLabel, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
            LedBarGauge(rpmFraction(), Modifier.weight(1f).height(16.dp), segments = 30, color = DashAmber)
        }
        }
    }
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color, alignEnd: Boolean = false) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1, softWrap = false)
        Text(value, style = MaterialTheme.typography.titleSmall, color = if (value == "--") DashGrey else color, maxLines = 1, softWrap = false)
    }
}

private fun levelColor(level: ReadoutLevel): Color = when (level) {
    ReadoutLevel.Unknown -> DashGrey
    ReadoutLevel.Ok -> LedGreen
    ReadoutLevel.Warn -> DashAmber
    ReadoutLevel.Bad -> AlertRed
}

/** A live value as a filled, bold chip; the selected one goes solid. */
@Composable
private fun ReadoutChip(label: String, value: String, level: ReadoutLevel, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color = levelColor(level)
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .clip(shape)
            .background(if (selected) color else color.copy(alpha = 0.22f))
            .skinFrame(shape, color, selected = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (selected) KittBlack else DashWhite, maxLines = 1, softWrap = false)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (selected) KittBlack else color, maxLines = 1, softWrap = false)
    }
}

/** The bigger info box that opens inside the car window: value, session range and what the number means. */
@Composable
private fun ReadoutCard(spec: CarReadout, value: Double?, stats: ReadoutStats, metric: Boolean, onClose: () -> Unit, modifier: Modifier) {
    val level = CarReadouts.level(spec.key, value)
    val color = levelColor(level)
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth(0.82f)
            .clip(shape)
            .background(KittBlack.copy(alpha = 0.92f))
            .skinFrame(shape, color, selected = true)
            .clickable(onClick = onClose)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = DashWhite, maxLines = 1)
            Text(CarReadouts.text(spec.key, value, metric), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1, softWrap = false)
        }
        if (stats.count > 0) {
            Text(
                "SINCE START   LOW ${CarReadouts.text(spec.key, stats.min, metric)}   HIGH ${CarReadouts.text(spec.key, stats.max, metric)}   AVG ${CarReadouts.text(spec.key, stats.average, metric)}",
                style = MaterialTheme.typography.labelMedium, color = DashAmber, maxLines = 2
            )
        } else {
            Text("No reading yet. This car may not report it.", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        }
        Text(spec.about, style = MaterialTheme.typography.bodySmall, color = DashWhite, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text("TAP TO CLOSE", style = MaterialTheme.typography.labelSmall, color = DashGrey)
    }
}

/** Everything live that is painted onto the car; [spin] and [pulse] only move while the car does. */
private class LiveCar(
    val coolantC: Double?, val rpm: Float, val speedKmh: Double?, val throttle: Double?, val load: Double?,
    val fuel: Double?, val volts: Double?, val mil: Boolean, val metric: Boolean, val spin: Float?, val pulse: Float
)

private fun heatColor(c: Double?): Color = when {
    c == null -> DashGrey
    c < 55 -> Color(0xFF42A5F5)
    c < 100 -> LedGreen
    c < 108 -> DashAmber
    else -> AlertRed
}
private fun voltColor(v: Double?): Color = when {
    v == null -> DashGrey
    v >= 13.2 -> LedGreen
    v >= 12.2 -> DashAmber
    else -> AlertRed
}
private fun fuelColor(f: Double?): Color = when {
    f == null -> DashGrey
    f > 25 -> LedGreen
    f > 10 -> DashAmber
    else -> AlertRed
}

/** Canvas anchors of the four tire callouts: LF bottom-left, RF top-left, LR bottom-right, RR top-right. */
private fun anchorFor(slot: Int, w: Float, h: Float, pad: Float): Offset = when (slot) {
    0 -> Offset(w * 0.12f, h - pad)
    1 -> Offset(w * 0.12f, pad)
    2 -> Offset(w * 0.88f, h - pad)
    else -> Offset(w * 0.88f, pad)
}

// ---- Rendered vehicle (Blender) with live data on top --------------------------------------------
// The bodies are rendered offline in Blender (see blender/make_car.py): white lines and a bloom on
// transparent PNGs that are tinted here to the active theme. Everything that moves or changes is
// drawn live over the picture at positions Blender recorded: the tire rings, the spinning spokes,
// the engine glow, the fuel bar, the battery lamp and the exhaust streaks.

private class WheelSpot(val slot: Int, val hub: Offset, val poly: List<Offset>, val near: Boolean)

private class CarArt(
    val lines: androidx.compose.ui.graphics.ImageBitmap,
    val glow: androidx.compose.ui.graphics.ImageBitmap,
    val fill: androidx.compose.ui.graphics.ImageBitmap,
    val w: Float, val h: Float,
    val wheels: List<WheelSpot>,
    val anchors: Map<String, Offset>
)

private class ArtIds(val lines: Int, val glow: Int, val fill: Int, val meta: Int)

private fun readArt(context: android.content.Context, ids: ArtIds): CarArt {
    fun bitmap(id: Int) = android.graphics.BitmapFactory.decodeResource(context.resources, id).asImageBitmap()
    val meta = org.json.JSONObject(context.resources.openRawResource(ids.meta).bufferedReader().use { it.readText() })
    val w = meta.getDouble("w").toFloat()
    val h = meta.getDouble("h").toFloat()
    fun pt(a: org.json.JSONArray) = Offset(a.getDouble(0).toFloat() * w, a.getDouble(1).toFloat() * h)
    val ws = meta.getJSONArray("wheels")
    val wheels = (0 until ws.length()).map { i ->
        val o = ws.getJSONObject(i)
        val poly = o.getJSONArray("poly")
        WheelSpot(o.getInt("slot"), pt(o.getJSONArray("hub")), (0 until poly.length()).map { pt(poly.getJSONArray(it)) }, o.getBoolean("near"))
    }
    val an = meta.getJSONObject("anchors")
    return CarArt(bitmap(ids.lines), bitmap(ids.glow), bitmap(ids.fill), w, h, wheels, an.keys().asSequence().associateWith { pt(an.getJSONArray(it)) })
}

// ---- Hologram vehicle -----------------------------------------------------------------------------
// The car is a Blender-rendered hologram turntable (blender/make_holo_turntable.py): 36 frames of a
// dense point-and-line cloud with a soft bloom, each a white image whose alpha is the glow, tinted
// here to the theme. The view turns slowly on its base by stepping through the frames. For every
// frame Blender recorded where the wheels and the engine, fuel tank, battery and exhaust are, so the
// live OBD data is painted onto the right parts as the car turns. Drawing is one image per frame, so
// it stays cheap on weak head units; lite mode holds a single three-quarter view.

/** The resting view (and the lite-mode view): the car from the front three-quarter, like the reference. */
private const val HOLO_REST_YAW = 5.67f

private class HoloWheel(val slot: Int, val hub: Offset, val poly: List<Offset>, val d: Float)

private class HoloFrame(val wheels: List<HoloWheel>, val anchors: Map<String, Offset>)

private class Flipbook(val kind: String, val w: Float, val h: Float, val frames: List<HoloFrame>)

private fun kindOf(skin: CockpitSkin) = when (skin) {
    CockpitSkin.Future -> "wedge"
    CockpitSkin.RoboCop -> "cruiser"
    else -> "coupe"
}

private fun readFlipbook(context: android.content.Context, skin: CockpitSkin): Flipbook {
    val kind = kindOf(skin)
    val id = context.resources.getIdentifier("holo_$kind", "raw", context.packageName)
    val o = org.json.JSONObject(context.resources.openRawResource(id).bufferedReader().use { it.readText() })
    fun pt(a: org.json.JSONArray) = Offset(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
    val fs = o.getJSONArray("frames")
    val frames = (0 until fs.length()).map { i ->
        val f = fs.getJSONObject(i)
        val ws = f.getJSONArray("wheels")
        val wheels = (0 until ws.length()).map { j ->
            val w = ws.getJSONObject(j)
            val poly = w.getJSONArray("poly")
            HoloWheel(w.getInt("slot"), pt(w.getJSONArray("hub")), (0 until poly.length()).map { pt(poly.getJSONArray(it)) }, w.getDouble("d").toFloat())
        }
        val an = f.getJSONObject("anchors")
        HoloFrame(wheels, an.keys().asSequence().associateWith { pt(an.getJSONArray(it)) })
    }
    return Flipbook(kind, o.getDouble("w").toFloat(), o.getDouble("h").toFloat(), frames)
}

/** The few frames in use stay decoded; the rest are read from the resources when the turntable reaches them. */
private val HoloBitmaps = object : LinkedHashMap<String, androidx.compose.ui.graphics.ImageBitmap>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, androidx.compose.ui.graphics.ImageBitmap>?) = size > 6
}

private fun holoBitmap(context: android.content.Context, book: Flipbook, index: Int): androidx.compose.ui.graphics.ImageBitmap {
    val name = "holo_${book.kind}_%02d".format(index)
    return HoloBitmaps.getOrPut(name) {
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        android.graphics.BitmapFactory.decodeResource(context.resources, id).asImageBitmap()
    }
}

/**
 * The whole turntable for the body on screen, decoded off the UI thread as alpha-only bitmaps (the
 * art is tinted anyway, so this is a quarter of the memory of ARGB). Switching body frees the old one.
 */
private object HoloFrames {
    private var kind: String? = null
    private var images: Array<androidx.compose.ui.graphics.ImageBitmap?> = emptyArray()

    fun load(context: android.content.Context, book: Flipbook, index: Int): androidx.compose.ui.graphics.ImageBitmap {
        val id = context.resources.getIdentifier("holo_${book.kind}_%02d".format(index), "drawable", context.packageName)
        val argb = android.graphics.BitmapFactory.decodeResource(context.resources, id)
        val alpha = argb.extractAlpha()
        argb.recycle()
        alpha.prepareToDraw()
        return alpha.asImageBitmap()
    }

    @Synchronized
    fun put(book: Flipbook, index: Int, image: androidx.compose.ui.graphics.ImageBitmap) {
        if (kind != book.kind || images.size != book.frames.size) {
            kind = book.kind
            images = arrayOfNulls(book.frames.size)
        }
        images[index] = image
    }

    @Synchronized
    fun get(book: Flipbook, index: Int): androidx.compose.ui.graphics.ImageBitmap? =
        if (kind == book.kind) images.getOrNull(index) else null
}

private fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** The recorded wheel rings and anchors part-way between two frames, so the live tags turn smoothly with the car. */
private fun HoloFrame.mixWith(other: HoloFrame, t: Float): HoloFrame {
    val wheels = wheels.map { w ->
        val o = other.wheels.firstOrNull { it.slot == w.slot } ?: return@map w
        HoloWheel(w.slot, lerpOffset(w.hub, o.hub, t), w.poly.mapIndexed { i, p -> lerpOffset(p, o.poly.getOrElse(i) { p }, t) }, w.d + (o.d - w.d) * t)
    }
    val anchors = anchors.mapValues { (k, v) -> other.anchors[k]?.let { lerpOffset(v, it, t) } ?: v }
    return HoloFrame(wheels, anchors)
}

/**
 * Draws [a] blended linearly into [b] by [mix]: both go into one layer, the second added with Plus,
 * so the brightness stays even through the blend instead of dipping like two half-faded images.
 */
private fun DrawScope.drawBlended(
    a: androidx.compose.ui.graphics.ImageBitmap, b: androidx.compose.ui.graphics.ImageBitmap?, mix: Float,
    dst: androidx.compose.ui.unit.IntSize, off: androidx.compose.ui.unit.IntOffset, alpha: Float,
    filter: androidx.compose.ui.graphics.ColorFilter
) {
    if (b == null || mix <= 0.005f) {
        drawImage(a, dstSize = dst, dstOffset = off, alpha = alpha, colorFilter = filter)
        return
    }
    val bounds = androidx.compose.ui.geometry.Rect(off.x.toFloat(), off.y.toFloat(), (off.x + dst.width).toFloat(), (off.y + dst.height).toFloat())
    drawContext.canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha })
    drawImage(a, dstSize = dst, dstOffset = off, alpha = 1f - mix, colorFilter = filter)
    drawImage(b, dstSize = dst, dstOffset = off, alpha = mix, colorFilter = filter, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
    drawContext.canvas.restore()
}

/** Point on a recorded tyre ring at [angle] (radians, 0 = toward the nose), interpolated between the 40 recorded points. */
private fun List<Offset>.at(angle: Float): Offset {
    val t = ((angle / (2f * PI.toFloat())) % 1f + 1f) % 1f * size
    val i = t.toInt() % size
    val f = t - t.toInt()
    val p = this[i]
    val q = this[(i + 1) % size]
    return Offset(p.x + (q.x - p.x) * f, p.y + (q.y - p.y) * f)
}

private fun DrawScope.drawHologram(
    skin: CockpitSkin, book: Flipbook, image: androidx.compose.ui.graphics.ImageBitmap,
    next: androidx.compose.ui.graphics.ImageBitmap?, mix: Float, frame: HoloFrame,
    line: Color, lf: Color, rf: Color, lr: Color, rr: Color, rpm: Float, live: LiveCar, scan: Float
) {
    val s = minOf(size.width / book.w, size.height / book.h) * 0.99f
    val tx = (size.width - book.w * s) / 2f
    val ty = (size.height - book.h * s) / 2f
    fun p(o: Offset) = Offset(tx + o.x * book.w * s, ty + o.y * book.h * s)
    val perMetre = book.w * s / 5.3f
    val thin = 1.dp.toPx()
    val hot = lerp(line, Color.White, 0.55f)
    val tires = listOf(lf, rf, lr, rr)
    val dst = androidx.compose.ui.unit.IntSize((book.w * s).roundToInt(), (book.h * s).roundToInt())
    val off = androidx.compose.ui.unit.IntOffset(tx.roundToInt(), ty.roundToInt())

    // Projector base: glowing rings on the floor under the car.
    val floor = p(Offset(0.5f, 0.86f))
    for ((r, a) in listOf(0.46f to 0.30f, 0.36f to 0.18f, 0.26f to 0.10f)) {
        drawOval(line.copy(alpha = a), Offset(floor.x - book.w * s * r, floor.y - book.h * s * r * 0.20f), Size(book.w * s * r * 2f, book.h * s * r * 0.40f), style = Stroke(thin * 1.4f))
    }
    // A squashed, faded mirror image on the floor, like the reflection under the car in the reference.
    val floorY = p(Offset(0f, 0.905f)).y
    withTransform({ scale(1f, -0.32f, Offset(0f, floorY)) }) {
        drawBlended(image, next, mix, dst, off, 0.20f, androidx.compose.ui.graphics.ColorFilter.tint(line, androidx.compose.ui.graphics.BlendMode.SrcIn))
    }
    // The hologram: the glow pass in the theme colour, then a lighter pass for the hot cores.
    drawBlended(image, next, mix, dst, off, 1f, androidx.compose.ui.graphics.ColorFilter.tint(line, androidx.compose.ui.graphics.BlendMode.SrcIn))
    drawBlended(image, next, mix, dst, off, 0.40f, androidx.compose.ui.graphics.ColorFilter.tint(hot, androidx.compose.ui.graphics.BlendMode.SrcIn))

    // Wheels: pressure-coloured ring on each tyre (the two nearest to the viewer at full strength) and spokes that turn with speed.
    val spin = live.spin ?: 0f
    val nearest = frame.wheels.sortedBy { it.d }.take(2).map { it.slot }.toSet()
    for (wh in frame.wheels) {
        val strong = wh.slot in nearest
        val color = tires[wh.slot]
        val ring = Path().apply {
            wh.poly.forEachIndexed { i, o -> val q = p(o); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
            close()
        }
        val a = if (strong) 1f else 0.40f
        drawPath(ring, color.copy(alpha = 0.10f * a), style = Stroke(thin * 4f))
        drawPath(ring, color.copy(alpha = 0.80f * a), style = Stroke(thin * 1.3f))
        val hub = p(wh.hub)
        if (live.spin != null) for (k in 0 until 12) {
            val ang = spin + k * (2f * PI.toFloat() / 12f)
            val end = p(wh.poly.at(ang))
            drawLine(hot.copy(alpha = 0.45f * a), Offset(hub.x + (end.x - hub.x) * 0.13f, hub.y + (end.y - hub.y) * 0.13f), Offset(hub.x + (end.x - hub.x) * 0.68f, hub.y + (end.y - hub.y) * 0.68f), thin)
        }
    }

    // Live data on the car: engine glow by heat, load and rpm, a fuel bar, a battery lamp, exhaust and speed streaks.
    fun a(name: String) = p(frame.anchors.getValue(name))
    val engine = a("engine")
    val beat = sin(live.pulse * 2f * PI.toFloat())
    val engineColor = if (live.mil && beat > 0f) DashAmber else heatColor(live.coolantC)
    val loadF = ((live.load ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val radius = perMetre * 0.55f * (1f + 0.10f * beat) * (0.8f + 0.5f * loadF)
    drawCircle(Brush.radialGradient(listOf(engineColor.copy(alpha = 0.50f), engineColor.copy(alpha = 0f)), center = engine, radius = radius), radius, engine)
    drawCircle(engineColor.copy(alpha = 0.75f), radius * 0.35f, engine, style = Stroke(thin * 1.4f))
    val battery = a("battery")
    drawCircle(voltColor(live.volts).copy(alpha = 0.3f), 8.dp.toPx(), battery)
    drawCircle(voltColor(live.volts), 3.5.dp.toPx(), battery)
    val tank = a("fuel")
    val frac = ((live.fuel ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val tw = 34.dp.toPx()
    val th = 6.dp.toPx()
    drawRect(Color.Black.copy(alpha = 0.7f), Offset(tank.x - tw / 2, tank.y - th / 2), Size(tw, th))
    drawRect(fuelColor(live.fuel), Offset(tank.x - tw / 2, tank.y - th / 2), Size(tw * frac, th))
    drawRect(fuelColor(live.fuel).copy(alpha = 0.8f), Offset(tank.x - tw / 2, tank.y - th / 2), Size(tw, th), style = Stroke(thin))
    val tail = a("tail")
    val to = a("tailTo")
    val len = hypot(to.x - tail.x, to.y - tail.y).coerceAtLeast(1f)
    val dir = Offset((to.x - tail.x) / len, (to.y - tail.y) / len)
    val across = Offset(-dir.y, dir.x)
    val exhaust = a("exhaust")
    val throttleF = ((live.throttle ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    for (k in 0..3) {
        val start = exhaust + across * ((k - 1.5f) * 3.dp.toPx())
        drawLine(engineColor.copy(alpha = 0.55f / (k + 1)), start, start + dir * (perMetre * (0.05f + 0.35f * throttleF)), thin * 2.2f, StrokeCap.Round)
    }
    val speedF = (((live.speedKmh ?: 0.0) / 140.0).toFloat()).coerceIn(0f, 1f)
    if (speedF > 0.05f) {
        for (k in -2..2) {
            val start = tail + across * (k * 12.dp.toPx()) + dir * 10.dp.toPx()
            drawLine(line.copy(alpha = 0.30f - 0.04f * abs(k)), start, start + dir * (perMetre * 1.1f * speedF), thin * 1.4f, StrokeCap.Round)
        }
    }

    // Scan band sweeping up the car.
    val by = size.height * (0.18f + 0.62f * scan)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, line.copy(alpha = 0.16f), Color.Transparent), startY = by - 14.dp.toPx(), endY = by + 14.dp.toPx()), Offset(0f, by - 14.dp.toPx()), Size(size.width, 28.dp.toPx()))

    // Tire callouts: a leader from each hub to its corner.
    val pad = 2.dp.toPx()
    for (wh in frame.wheels) {
        val hub = p(wh.hub)
        val anchor = anchorFor(wh.slot, size.width, size.height, pad)
        val elbow = Offset(hub.x, anchor.y)
        val a = if (wh.slot in nearest) 0.45f else 0.22f
        drawLine(tires[wh.slot].copy(alpha = a), hub, elbow, thin)
        drawLine(tires[wh.slot].copy(alpha = a), elbow, anchor, thin)
        drawCircle(tires[wh.slot].copy(alpha = a + 0.3f), 2.dp.toPx(), hub)
    }

    // Value tags on the parts they describe.
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 11.dp.toPx()
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
    }
    drawIntoCanvas { cv ->
        fun tag(text: String, at: Offset, color: Color) {
            val half = paint.measureText(text) / 2f + 4.dp.toPx()
            val bg = android.graphics.Paint().apply { this.color = android.graphics.Color.argb(190, 0, 0, 0) }
            cv.nativeCanvas.drawRoundRect(at.x - half, at.y - paint.textSize, at.x + half, at.y + 4.dp.toPx(), 4.dp.toPx(), 4.dp.toPx(), bg)
            paint.color = color.toArgb()
            cv.nativeCanvas.drawText(text, at.x, at.y, paint)
        }
        val coolant = live.coolantC?.let { if (live.metric) "${it.roundToInt()}°C" else "${(it * 9 / 5 + 32).roundToInt()}°F" } ?: "--"
        tag(coolant, engine + Offset(0f, -24.dp.toPx()), heatColor(live.coolantC))
        if (live.mil) tag("CHECK ENGINE", engine + Offset(0f, -37.dp.toPx()), DashAmber)
        tag(live.fuel?.let { "${it.roundToInt()}%" } ?: "--", tank + Offset(0f, 22.dp.toPx()), fuelColor(live.fuel))
        tag(live.volts?.let { "%.1fV".format(it) } ?: "--", battery + Offset(0f, 22.dp.toPx()), voltColor(live.volts))
    }

    // Film overlays.
    val c = Offset(size.width / 2f, size.height * 0.5f)
    when (skin) {
        CockpitSkin.Terminator -> {
            val r = size.height * 0.34f
            drawCircle(line.copy(alpha = 0.8f), r, c, style = Stroke(thin))
            drawLine(line, Offset(c.x - r * 1.5f, c.y), Offset(c.x - r * 0.8f, c.y), thin)
            drawLine(line, Offset(c.x + r * 0.8f, c.y), Offset(c.x + r * 1.5f, c.y), thin)
            drawLine(line, Offset(c.x, c.y - r * 1.4f), Offset(c.x, c.y - r * 0.8f), thin)
            val b = 14.dp.toPx()
            for ((cx, cy, dx, dy) in listOf(listOf(0f, 0f, 1f, 1f), listOf(size.width, 0f, -1f, 1f), listOf(0f, size.height, 1f, -1f), listOf(size.width, size.height, -1f, -1f))) {
                drawLine(line, Offset(cx, cy), Offset(cx + dx * b, cy), 2.dp.toPx())
                drawLine(line, Offset(cx, cy), Offset(cx, cy + dy * b), 2.dp.toPx())
            }
        }
        CockpitSkin.Matrix -> {
            val glyphs = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎ0123456789"
            val gp = android.graphics.Paint().apply { isAntiAlias = true; textSize = 11.dp.toPx(); typeface = android.graphics.Typeface.MONOSPACE }
            drawIntoCanvas { cv ->
                for (col in 0..11) {
                    val x = size.width * (0.03f + col * 0.082f)
                    val length = 4 + (col * 5) % 6
                    val bottom = size.height * (0.30f + 0.06f * ((col * 7) % 5))
                    for (i in 0 until length) {
                        gp.color = (if (i == length - 1) Color(0xFFCFFFD8) else line.copy(alpha = 0.10f + 0.30f * i / length)).toArgb()
                        cv.nativeCanvas.drawText(glyphs[(col * 31 + i * 17) % glyphs.length].toString(), x, bottom - (length - 1 - i) * gp.textSize * 1.15f, gp)
                    }
                }
            }
        }
        CockpitSkin.Future -> {
            val dc = Offset(size.width * 0.5f, size.height * 0.14f)
            val dr = size.height * 0.11f
            drawArc(line.copy(alpha = 0.7f), 150f, 240f, false, Offset(dc.x - dr, dc.y - dr), Size(dr * 2, dr * 2), style = Stroke(thin * 2))
            val ang = Math.toRadians((150.0 + 240.0 * rpm.coerceIn(0f, 1f)))
            drawLine(Color(0xFFFF3B30), dc, Offset(dc.x + dr * 0.9f * cos(ang).toFloat(), dc.y + dr * 0.9f * sin(ang).toFloat()), 2.dp.toPx())
            drawCircle(line, 2.dp.toPx(), dc)
        }
        else -> Unit
    }
}

/** The chassis art ships with KITT Holo only (src/holo/res), so it is looked up by name. */
private fun chassisIds(context: android.content.Context): ArtIds {
    fun id(name: String, type: String) = context.resources.getIdentifier(name, type, context.packageName)
    return ArtIds(id("car_chassis_lines", "drawable"), id("car_chassis_glow", "drawable"), id("car_chassis_fill", "drawable"), id("car_chassis", "raw"))
}

/**
 * The Terminator's top-down chassis: the Blender-rendered endoskeleton tinted to the theme, with the
 * four tires ringed in their pressure colours ([tires] = LF, RF, LR, RR) and a glow on the engine in
 * [engine] (its heat colour).
 */
@Composable
fun ChassisPlan(line: Color, tires: List<Color>, engine: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val art = remember { readArt(context, chassisIds(context)) }
    Canvas(modifier) {
        val s = minOf(size.width / art.w, size.height / art.h)
        val tx = (size.width - art.w * s) / 2f
        val ty = (size.height - art.h * s) / 2f
        fun p(o: Offset) = Offset(tx + o.x * s, ty + o.y * s)
        val dst = androidx.compose.ui.unit.IntSize((art.w * s).roundToInt(), (art.h * s).roundToInt())
        val off = androidx.compose.ui.unit.IntOffset(tx.roundToInt(), ty.roundToInt())
        val hot = lerp(line, Color.White, 0.6f)
        drawImage(art.glow, dstSize = dst, dstOffset = off, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(line, androidx.compose.ui.graphics.BlendMode.SrcIn))
        drawImage(art.lines, dstSize = dst, dstOffset = off, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(hot, androidx.compose.ui.graphics.BlendMode.SrcIn))
        for (wh in art.wheels) {
            val ring = Path().apply {
                wh.poly.forEachIndexed { i, o -> val q = p(o); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) }
                close()
            }
            val color = tires.getOrElse(wh.slot) { line }
            drawPath(ring, color.copy(alpha = 0.22f))
            drawPath(ring, color, style = Stroke(2.dp.toPx()))
        }
        val e = p(art.anchors.getValue("engine"))
        val r = art.w * s * 0.28f
        drawCircle(Brush.radialGradient(listOf(engine.copy(alpha = 0.5f), engine.copy(alpha = 0f)), center = e, radius = r), r, e)
    }
}

/**
 * Middle/right pane of the Drive page: the vehicle HUD, with a toggle to the pinned app grid
 * ([appsContent]) so nothing from the original Systems pane is lost.
 */
@Composable
fun VehicleDashPane(
    modifier: Modifier,
    reading: (String) -> PidReading?,
    metric: Boolean,
    rpmFraction: () -> Float,
    appsContent: @Composable (Modifier) -> Unit
) {
    val skin = CockpitSkin.current()
    var showApps by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KittButton("Vehicle HUD", { showApps = false }, Modifier.weight(1f).fillMaxHeight(), DashAmber, active = !showApps)
            KittButton("Apps", { showApps = true }, Modifier.weight(1f).fillMaxHeight(), DashAmber, active = showApps)
        }
        if (showApps) {
            appsContent(Modifier.weight(1f).fillMaxWidth())
        } else {
            KittPanel(title = skin.hudTitle, modifier = Modifier.weight(1f).fillMaxWidth(), accent = DashAmber) {
                VehicleStatusHud(reading, metric, rpmFraction, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * A category tab for the Games page sidebar, framed per film: KITT's neon arrow tab, the
 * Terminator's targeting brackets, the Matrix's terminal prompt, a Back to the Future Dymo label
 * and RoboCop's hazard-striped plate.
 */
@Composable
fun SkinTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, arrow: Boolean = false) {
    val skin = CockpitSkin.current()
    val accent = if (selected) DashAmber else ScannerRed
    val shape: androidx.compose.ui.graphics.Shape = if (arrow && selected) ArrowTabShape else RoundedCornerShape(if (skin == CockpitSkin.Future) 4.dp else 10.dp)
    val text = when (skin) {
        CockpitSkin.Matrix -> "> ${label.uppercase()}${if (selected) "_" else ""}"
        else -> label.uppercase()
    }
    Box(
        modifier
            .clip(shape)
            .background(
                when (skin) {
                    CockpitSkin.Future -> Color(0xFF111111)
                    else -> accent.copy(alpha = if (selected) 0.18f else 0.08f)
                }
            )
            .border(if (selected) 2.dp else 1.dp, if (skin == CockpitSkin.Future) Color(0xFFB0B0B0) else accent, shape)
            .clickable(onClick = onClick)
            .drawBehind {
                when (skin) {
                    CockpitSkin.Terminator -> {
                        val b = 10.dp.toPx()
                        drawLine(accent, Offset(0f, 0f), Offset(b, 0f), 3.dp.toPx())
                        drawLine(accent, Offset(0f, 0f), Offset(0f, b), 3.dp.toPx())
                        drawLine(accent, Offset(size.width, size.height), Offset(size.width - b, size.height), 3.dp.toPx())
                        drawLine(accent, Offset(size.width, size.height), Offset(size.width, size.height - b), 3.dp.toPx())
                    }
                    CockpitSkin.RoboCop -> {
                        var x = 0f
                        while (x < 14.dp.toPx()) {
                            drawLine(DashYellow, Offset(x, size.height), Offset(x + 8.dp.toPx(), 0f), 3.dp.toPx())
                            x += 10.dp.toPx()
                        }
                    }
                    CockpitSkin.Kitt -> if (selected) drawLine(accent, Offset(size.width - 2f, 0f), Offset(size.width - 2f, size.height), 4.dp.toPx())
                    else -> Unit
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (skin == CockpitSkin.Future) DashWhite else accent,
            fontFamily = if (skin == CockpitSkin.Matrix || skin == CockpitSkin.Terminator) FontFamily.Monospace else null,
            fontWeight = if (skin == CockpitSkin.Future) FontWeight.ExtraBold else null,
            maxLines = 1
        )
    }
}


private fun DrawScope.decorate(skin: CockpitSkin, edge: Color) {
    val thick = 3.dp.toPx()
    when (skin) {
        CockpitSkin.Terminator -> {
            val b = 12.dp.toPx()
            drawLine(edge, Offset(0f, 0f), Offset(b, 0f), thick)
            drawLine(edge, Offset(0f, 0f), Offset(0f, b), thick)
            drawLine(edge, Offset(size.width, size.height), Offset(size.width - b, size.height), thick)
            drawLine(edge, Offset(size.width, size.height), Offset(size.width, size.height - b), thick)
        }
        CockpitSkin.Matrix -> {
            // Phosphor corner ticks, like a terminal's bezel marks.
            val b = 8.dp.toPx()
            for ((x, y, dx, dy) in listOf(listOf(0f, 0f, 1f, 1f), listOf(size.width, 0f, -1f, 1f), listOf(0f, size.height, 1f, -1f), listOf(size.width, size.height, -1f, -1f))) {
                drawLine(edge, Offset(x, y), Offset(x + dx * b, y), 2.dp.toPx())
                drawLine(edge, Offset(x, y), Offset(x, y + dy * b), 2.dp.toPx())
            }
        }
        CockpitSkin.Future -> for (p in listOf(
            Offset(7.dp.toPx(), 7.dp.toPx()), Offset(size.width - 7.dp.toPx(), 7.dp.toPx()),
            Offset(7.dp.toPx(), size.height - 7.dp.toPx()), Offset(size.width - 7.dp.toPx(), size.height - 7.dp.toPx())
        )) drawCircle(edge, 2.5.dp.toPx(), p)
        CockpitSkin.RoboCop -> {
            var x = 0f
            while (x < size.width) {
                drawLine(DashYellow, Offset(x, size.height), Offset(x + 6.dp.toPx(), size.height - 6.dp.toPx()), 2.dp.toPx())
                x += 10.dp.toPx()
            }
        }
        CockpitSkin.Kitt -> Unit
    }
}

/**
 * Border and corner decoration for a panel or tile, per film: the Terminator's targeting
 * brackets, the Matrix's phosphor ticks, Back to the Future's riveted steel, RoboCop's hazard
 * stripe, KITT's plain neon edge.
 */
fun Modifier.skinFrame(shape: androidx.compose.ui.graphics.Shape, accent: Color, selected: Boolean = false): Modifier {
    val skin = CockpitSkin.current()
    val edge = if (skin == CockpitSkin.Future) Color(0xFFB0B0B0) else accent
    return this
        .border(if (selected) 2.dp else 1.5.dp, edge.copy(alpha = if (selected) 1f else 0.8f), shape)
        .drawBehind { decorate(skin, edge) }
}

/**
 * The AI page's status terminal, a different instrument per film: KITT's voice modulator, the
 * Terminator's scrolling system lines, the Matrix operator console, Back to the Future's three
 * time circuits (destination / present / last departed) and RoboCop's Prime Directives.
 */
@Composable
fun SkinTerminal(modifier: Modifier = Modifier) {
    val skin = CockpitSkin.current()
    val shape = MaterialTheme.shapes.small
    val mono = FontFamily.Monospace
    Box(modifier.clip(shape).background(Color.Black).skinFrame(shape, ScannerRed).padding(8.dp)) {
        when (skin) {
            CockpitSkin.Kitt -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                VoiceModulator(active = false, modifier = Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1.4f)) {
                    Text("KITT CO-PILOT", style = MaterialTheme.typography.titleSmall, color = ScannerRed)
                    Text("VOICE CIRCUITS ONLINE", style = MaterialTheme.typography.labelSmall, color = DashAmber)
                }
            }
            CockpitSkin.Terminator -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                listOf("0300: A0 0A 38  LDY #10  SEC", "MODE: SCAN   CPU: ONLINE", "ANALYSIS: NO THREAT", "MISSION: DRIVE").forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = ScannerRed, fontFamily = mono, maxLines = 1)
                }
            }
            CockpitSkin.Matrix -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                listOf(">_ OPERATOR CONSOLE", "TRACE PROGRAM: RUNNING", "SIGNAL: CLEAN", "STANDING BY_").forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = ScannerRed, fontFamily = mono, maxLines = 1)
                }
            }
            CockpitSkin.Future -> {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val departed = remember { System.currentTimeMillis() }
                LaunchedEffect(Unit) {
                    while (true) {
                        now = System.currentTimeMillis()
                        kotlinx.coroutines.delay(1_000)
                    }
                }
                val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.US) }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        Triple("DESTINATION TIME", "--:--", Color(0xFFFF3B30)),
                        Triple("PRESENT TIME", fmt.format(java.util.Date(now)), LedGreen),
                        Triple("LAST TIME DEPARTED", fmt.format(java.util.Date(departed)), DashAmber)
                    ).forEach { (label, value, color) ->
                        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
                            SegmentDisplay(value, Modifier.weight(1f).fillMaxHeight(), color, cells = 5)
                        }
                    }
                }
            }
            CockpitSkin.RoboCop -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Text("PRIME DIRECTIVES", style = MaterialTheme.typography.labelMedium, color = DashYellow, fontFamily = mono, maxLines = 1)
                listOf("1. SERVE THE PUBLIC TRUST", "2. PROTECT THE INNOCENT", "3. UPHOLD THE LAW", "4. [CLASSIFIED]").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = mono, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The page selector as one illuminated console strip, like the design: italic caps, the active
 * page lit in the theme's accent with a glow, framed per film.
 */
@Composable
fun ConsoleTabs(labels: List<String>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val inner = MaterialTheme.shapes.small
    Row(
        modifier.clip(shape).background(KittBlack).skinFrame(shape, ScannerRed).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == current
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(inner)
                    .background(if (on) ScannerRed.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.ExtraBold,
                        shadow = if (on) Shadow(ScannerRed, Offset.Zero, 14f) else null
                    ),
                    color = if (on) ScannerRed else ScannerRed.copy(alpha = 0.65f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}


/** A tab whose right edge points at the content, for the selected entry of a sidebar. */
private val ArrowTabShape = GenericShape { size, _ ->
    val tip = size.height * 0.35f
    moveTo(0f, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(0f, size.height)
    close()
}

/** A labelled pill switch with ACTIVE / INACTIVE underneath, like the game-mode panel in the design. */
@Composable
fun ModeSwitch(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(11.dp)
    Column(modifier.clickable(onClick = onClick).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (on) DashWhite else DashGrey, maxLines = 1, softWrap = false)
        Box(
            Modifier.width(46.dp).height(22.dp).clip(pill)
                .background(if (on) ScannerRed.copy(alpha = 0.85f) else Color(0xFF2A2A2A))
                .border(1.dp, if (on) ScannerRed else DashGrey.copy(alpha = 0.6f), pill)
        ) {
            Box(
                Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).padding(2.dp).size(18.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(if (on) Color.White else DashGrey)
            )
        }
        Text(if (on) "ACTIVE" else "INACTIVE", style = MaterialTheme.typography.labelSmall, color = if (on) ScannerRed else DashGrey, maxLines = 1, softWrap = false)
    }
}
