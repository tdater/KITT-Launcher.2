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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
private val TireIds = PidCatalog.TIRES

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
        obd.watch("vehicle_hud", TireIds.toSet() + PidCatalog.COOLANT + PidCatalog.MODULE_VOLTAGE + PidCatalog.ADAPTER_VOLTAGE + PidCatalog.SPEED + PidCatalog.THROTTLE + PidCatalog.ENGINE_LOAD + PidCatalog.FUEL_LEVEL + PidCatalog.RPM + PidCatalog.MAF + CarReadouts.PIDS)
        onDispose { obd.unwatch("vehicle_hud") }
    }
    fun fresh(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 120_000 }?.value
    fun tire(id: String): String {
        val kpa = fresh(id) ?: return "--"
        return if (metric) "${kpa.roundToInt()} kPa" else "${(kpa * KPA_TO_PSI).roundToInt()} PSI"
    }
    fun tireColor(id: String): Color {
        val psi = (fresh(id) ?: return DashAmber) * KPA_TO_PSI
        return if (psi in 26.0..40.0) LedGreen else AlertRed
    }
    val coolant = fresh(PidCatalog.COOLANT)?.let { if (metric) "${it.roundToInt()}°C" else "${(it * 9 / 5 + 32).roundToInt()}°F" } ?: "--"
    val volts = (fresh(PidCatalog.MODULE_VOLTAGE) ?: fresh(PidCatalog.ADAPTER_VOLTAGE))?.let { "%.1fV".format(it) } ?: "--"

    val lite = LocalLiteMode.current
    val context = LocalContext.current
    val dtcs by obd.dtcs.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    val statPids = remember { CarReadouts.PIDS + TireIds + PidCatalog.COOLANT + PidCatalog.MODULE_VOLTAGE + PidCatalog.RPM }
    val stats = remember { statPids.associateWith { ReadoutStats() } }
    val readingNow = rememberUpdatedState(reading)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            for (pid in statPids) {
                readingNow.value(pid)?.takeIf { System.currentTimeMillis() - it.timestamp < 120_000 }?.let { stats.getValue(pid).add(it.value) }
            }
        }
    }
    val spin = remember { mutableFloatStateOf(0f) }
    val pulse = remember { mutableFloatStateOf(0f) }
    val turn = remember { mutableFloatStateOf(HOLO_REST_YAW) }
    val scanPhase = remember { mutableFloatStateOf(0f) }
    val userInteractingUntil = remember { mutableLongStateOf(0L) }
    val speedNow = rememberUpdatedState(fresh(PidCatalog.SPEED) ?: 0.0)
    // The spokes and the engine glow only move while the car does; in lite mode nothing animates.
    LaunchedEffect(lite) {
        if (lite) return@LaunchedEffect
        // The hologram turns slowly on its base unless the user is manually rotating or inspecting a part.
        var last = SystemClock.uptimeMillis()
        while (true) {
            delay(33)
            val now = SystemClock.uptimeMillis()
            val elapsed = now - last
            last = now
            if (now >= userInteractingUntil.longValue) {
                turn.floatValue = Turntable.advance(turn.floatValue, elapsed)
            }
            scanPhase.floatValue = (scanPhase.floatValue + 0.24f * elapsed.coerceAtMost(250L) / 1000f) % 1f
            pulse.floatValue = (pulse.floatValue + (0.65f + rpmFraction() * 2.6f) * (elapsed.coerceAtMost(250L) / 1000f)) % 1f
        }
    }
    LaunchedEffect(lite) {
        if (lite) return@LaunchedEffect
        while (true) {
            delay(66)
            if (speedNow.value > 3.0) spin.floatValue = (spin.floatValue + (speedNow.value / 10.0 * 0.25).coerceAtMost(8.0).toFloat() * 0.066f) % (2f * PI.toFloat())
        }
    }
    val milOn = dtcs?.all?.isNotEmpty() == true
    val kittSettings = (LocalContext.current.applicationContext as KittApplication).settings
    val vehicle by kittSettings.holoVehicle.state.collectAsState()
    val kind = kindOf(skin, VehicleChoice.decode(vehicle))
    val book = remember(kind) { readFlipbook(context, kind) }
    // Decode every frame off the UI thread before the turntable reaches it (lite mode keeps one view).
    val ready = remember(book) { mutableIntStateOf(0) }
    LaunchedEffect(book, lite) {
        if (lite) return@LaunchedEffect
        val start = Turntable.blend(turn.floatValue, book.frames.size).from
        for (i in Turntable.preloadOrder(start, book.frames.size)) {
            if (HoloFrames.get(book, i) == null) {
                val image = withContext(Dispatchers.IO) { HoloFrames.load(context, book, i) }
                HoloFrames.put(book, i, image)
            }
            ready.intValue++
        }
    }
    // Live values as callouts around the vehicle: engine, speed, battery and fuel above; the four tyres below.
    val speedV = fresh(PidCatalog.SPEED)
    val coolantV = fresh(PidCatalog.COOLANT)
    val voltsV = fresh(PidCatalog.MODULE_VOLTAGE) ?: fresh(PidCatalog.ADAPTER_VOLTAGE)
    val fuelV = fresh(PidCatalog.FUEL_LEVEL)
    val rpmV = fresh(PidCatalog.RPM)
    fun tireLevel(id: String): ReadoutLevel {
        val kpa = fresh(id) ?: return ReadoutLevel.Unknown
        return if (kpa * KPA_TO_PSI in 26.0..40.0) ReadoutLevel.Ok else ReadoutLevel.Bad
    }
    fun tireState(id: String): String? {
        val psi = (fresh(id) ?: return null) * KPA_TO_PSI
        return if (psi < 26.0) "LOW" else if (psi > 40.0) "HIGH" else "OK"
    }
    val engineLevel = when {
        coolantV == null -> ReadoutLevel.Unknown
        coolantV >= 108 -> ReadoutLevel.Bad
        coolantV >= 100 -> ReadoutLevel.Warn
        else -> ReadoutLevel.Ok
    }
    val voltLevel = when {
        voltsV == null -> ReadoutLevel.Unknown
        voltsV < 11.8 || voltsV > 15.2 -> ReadoutLevel.Bad
        voltsV < 12.4 -> ReadoutLevel.Warn
        else -> ReadoutLevel.Ok
    }
    val rpmText = rpmV?.let { "RPM %,d".format(it.roundToInt()) }
    val burnV = PidCatalog.fuelRateLph(fresh(PidCatalog.FUEL_RATE), fresh(PidCatalog.MAF))
    val lastSlots = remember { mutableMapOf<String, Int>() }
    val fuelCallout = if (fuelV != null || burnV == null) {
        Callout("fuel", "FUEL", CarReadouts.text("fuel", fuelV, metric), if (burnV != null) CarReadouts.text("flow", burnV, metric) else null, CarReadouts.level("fuel", fuelV), lastSlots["fuel"] ?: 3, "fuel")
    } else {
        Callout("fuel", "FUEL BURN", CarReadouts.text("flow", burnV, metric), rpmText, ReadoutLevel.Ok, lastSlots["fuel"] ?: 3, "fuel")
    }
    val bottomCallouts = CarReadouts.bottomCallouts(
        tirePrefix = skin.tireLabel,
        tiresKpa = TireIds.map(::fresh),
        metric = metric,
        load = fresh(PidCatalog.ENGINE_LOAD),
        throttle = fresh(PidCatalog.THROTTLE),
        intakeC = fresh(PidCatalog.INTAKE_TEMP),
        burnLph = burnV,
        rpm = rpmV,
        lastSlots = lastSlots
    )
    val callouts = listOf(
        Callout("engine", skin.coolantLabel, coolant, if (milOn) "CHECK ENGINE" else rpmText, if (milOn) ReadoutLevel.Warn else engineLevel, lastSlots["engine"] ?: 0, "engine"),
        Callout("speed", "SPEED", CarReadouts.text("speed", speedV, metric), null, CarReadouts.level("speed", speedV), lastSlots["speed"] ?: 1, "roof"),
        Callout("battery", skin.batteryLabel, volts, null, voltLevel, lastSlots["battery"] ?: 2, "battery"),
        fuelCallout,
    ) + bottomCallouts
    val calloutsNow = rememberUpdatedState(callouts)

    fun lowHighAvg(pid: String, fmt: (Double) -> String): List<Pair<String, String>> {
        val st = stats[pid]?.takeIf { it.count > 0 } ?: return emptyList()
        return listOf("LOW SINCE START" to fmt(st.min), "HIGH SINCE START" to fmt(st.max), "AVERAGE" to fmt(st.average))
    }
    fun temp(c: Double) = if (metric) "${c.roundToInt()}°C" else "${(c * 9 / 5 + 32).roundToInt()}°F"
    fun detailFor(key: String): CalloutDetail? {
        val c = callouts.firstOrNull { it.key == key } ?: return null
        return when {
            key == "engine" -> CalloutDetail(
                skin.coolantLabel, c.value, c.level,
                listOfNotNull(
                    rpmV?.let { "ENGINE SPEED" to "%,d rpm".format(it.roundToInt()) },
                    "LOAD" to CarReadouts.text("load", fresh(PidCatalog.ENGINE_LOAD), metric),
                    "THROTTLE" to CarReadouts.text("throttle", fresh(PidCatalog.THROTTLE), metric),
                    "INTAKE AIR" to CarReadouts.text("intake", fresh(PidCatalog.INTAKE_TEMP), metric),
                    "FUEL BURN" to CarReadouts.text("flow", burnV, metric),
                ) + lowHighAvg(PidCatalog.COOLANT, ::temp),
                (if (milOn) "The engine computer has stored a fault code: open Diagnostics to read it. " else "") +
                    "Engine coolant temperature. Normal is about 195-220°F (90-105°C). Climbing towards 230°F (110°C) means overheating: ease off, and stop somewhere safe if it keeps rising."
            )
            key == "speed" -> CalloutDetail("SPEED", c.value, c.level, lowHighAvg(PidCatalog.SPEED) { CarReadouts.text("speed", it, metric) }, CarReadouts.byKey("speed")?.about.orEmpty())
            key == "battery" -> CalloutDetail(
                skin.batteryLabel, c.value, c.level, lowHighAvg(PidCatalog.MODULE_VOLTAGE) { "%.1f V".format(it) },
                "Voltage at the car's computer. About 12.4-12.8 V with the engine off and 13.5-14.8 V while the alternator charges. Under 12 V when parked points to a weak battery."
            )
            key == "fuel" -> CalloutDetail(
                "FUEL", c.value, c.level,
                listOf("FUEL BURN" to CarReadouts.text("flow", burnV, metric)) + lowHighAvg(PidCatalog.FUEL_LEVEL) { CarReadouts.text("fuel", it, metric) },
                CarReadouts.byKey("fuel")?.about.orEmpty()
            )
            key.startsWith("tire") -> {
                val i = key.removePrefix("tire").toIntOrNull() ?: return null
                val id = TireIds.getOrNull(i) ?: return null
                val kpa = fresh(id)
                if (TireIds.any { fresh(it) != null } || c.title.startsWith(skin.tireLabel)) {
                    CalloutDetail(
                        c.title, c.value, c.level,
                        listOfNotNull(
                            kpa?.let { "PRESSURE" to "${it.roundToInt()} kPa / ${(it * KPA_TO_PSI).roundToInt()} PSI" },
                            tireState(id)?.let { "STATUS" to it },
                        ) + lowHighAvg(id) { if (metric) "${it.roundToInt()} kPa" else "${(it * KPA_TO_PSI).roundToInt()} PSI" },
                        "Tyre pressure from the car's sensors. Match the placard on the driver's door jamb, usually 30-36 PSI (210-250 kPa), checked cold. Low pressure wears tyres and costs fuel."
                    )
                } else {
                    val specKey = listOf("load", "throttle", "intake", "flow").getOrNull(i) ?: "load"
                    val spec = CarReadouts.byKey(specKey)
                    val pid = spec?.pid ?: PidCatalog.ENGINE_LOAD
                    CalloutDetail(
                        c.title, c.value, c.level,
                        lowHighAvg(pid) { CarReadouts.text(specKey, it, metric) },
                        spec?.about ?: "Live engine reading from the vehicle computer (shown while TPMS sensors are offline)."
                    )
                }
            }
            else -> null
        }
    }

    Box(modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            VehicleSwitch(VehicleChoice.decode(vehicle), { kittSettings.holoVehicle.set(it.key) })
            Text(skin.tachLabel, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
            LedBarGauge(rpmFraction(), Modifier.weight(1f).height(14.dp), segments = 30, color = DashAmber)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(book) {
                val touchSlopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPos = down.position
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!dragged) {
                                val at = change.position
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val boxes = CalloutLayout.boxes(w, h, density)
                                val yaw = turn.floatValue
                                val blend = Turntable.blend(yaw, book.frames.size)
                                val frame = if (blend.mix > 0f) book.frames[blend.from].mixWith(book.frames[blend.to], blend.mix) else book.frames[blend.from]
                                val s = minOf((w * 1.14f) / book.w, (boxes.bandHeight * 1.28f) / book.h)
                                val tx = (w - book.w * s) / 2f
                                val ty = boxes.bandTop + (boxes.bandHeight - book.h * s) / 2f
                                fun p(o: Offset) = Offset(tx + o.x * book.w * s, ty + o.y * book.h * s)
                                fun anchorPos(name: String): Offset = when {
                                    name == "engine" -> p(frame.anchors.getValue("engine"))
                                    name == "battery" -> p(frame.anchors.getValue("battery"))
                                    name == "fuel" -> p(frame.anchors.getValue("fuel"))
                                    name == "roof" -> frame.anchors["roof"]?.let(::p) ?: p(Offset(0.5f, 0.30f))
                                    name.startsWith("w") -> frame.wheels.firstOrNull { it.slot == name.drop(1).toIntOrNull() }?.let { p(it.hub) } ?: p(frame.anchors.getValue("engine"))
                                    else -> p(frame.anchors.getValue("engine"))
                                }
                                // 1. Check direct touch on the 3D vehicle anchor area (engine, battery, fuel, roof, or 4 wheels)
                                val hitAnchorKey = CalloutLayout.hitAnchor(
                                    x = at.x,
                                    y = at.y,
                                    callouts = calloutsNow.value,
                                    anchorPos = ::anchorPos,
                                    maxRadius = 32.dp.toPx()
                                )
                                // 2. Or check touch on the futuristic callout capsule itself
                                val slot = boxes.hit(at.x, at.y, 8.dp.toPx())
                                val hitBoxKey = calloutsNow.value.firstOrNull { it.slot == slot }?.key
                                val key = hitAnchorKey ?: hitBoxKey
                                selected = if (key == null || key == selected) null else key
                                if (selected != null) {
                                    userInteractingUntil.longValue = SystemClock.uptimeMillis() + 6_000L
                                }
                            }
                            break
                        }
                        val totalDx = change.position.x - startPos.x
                        val totalDy = change.position.y - startPos.y
                        if (!dragged && hypot(totalDx, totalDy) > touchSlopPx) {
                            dragged = true
                        }
                        if (dragged) {
                            val dx = change.positionChange().x
                            if (abs(dx) > 0.1f) {
                                turn.floatValue = Turntable.drag(turn.floatValue, dx, size.width.toFloat())
                                userInteractingUntil.longValue = SystemClock.uptimeMillis() + 4_500L
                            }
                            change.consume()
                        }
                    }
                }
            }
        ) {
            // The animation clocks are read here, in the draw pass, so only this canvas redraws as they tick.
            val moving = !lite && (speedNow.value > 3.0)
            val live = LiveCar(
                coolantC = fresh(PidCatalog.COOLANT), rpm = rpmFraction(), speedKmh = fresh(PidCatalog.SPEED), throttle = fresh(PidCatalog.THROTTLE),
                load = fresh(PidCatalog.ENGINE_LOAD), fuel = fresh(PidCatalog.FUEL_LEVEL), volts = fresh(PidCatalog.MODULE_VOLTAGE) ?: fresh(PidCatalog.ADAPTER_VOLTAGE),
                mil = milOn, metric = metric, spin = if (moving) spin.floatValue else null, pulse = if (lite) 0f else pulse.floatValue
            )
            // Even in lite mode, reflect manual touch rotation if the user dragged the vehicle.
            val yaw = turn.floatValue
            ready.intValue // redraw as frames arrive
            val blend = Turntable.blend(yaw, book.frames.size)
            val first = HoloFrames.get(book, blend.from)
            val second = HoloFrames.get(book, blend.to)
            val image = first ?: holoBitmap(context, book, blend.from)
            // Until both neighbours are decoded (and always in lite mode) show a single frame.
            val mix = if (!lite && first != null && second != null) blend.mix else 0f
            val frame = if (mix > 0f) book.frames[blend.from].mixWith(book.frames[blend.to], mix) else book.frames[blend.from]
            fun wheelColor(idx: Int): Color = if (fresh(TireIds[idx]) != null) tireColor(TireIds[idx]) else bottomCallouts.getOrNull(idx)?.level?.takeIf { it != ReadoutLevel.Unknown }?.let(::levelColor) ?: DashAmber
            drawHologram(skin, book, image, second, mix, frame, ScannerRed, wheelColor(0), wheelColor(1), wheelColor(2), wheelColor(3), rpmFraction(), live, scanPhase.floatValue, callouts, selected)
            for (c in callouts) lastSlots[c.key] = c.slot
        }
        selected?.let { key ->
            detailFor(key)?.let { detail ->
                CalloutCard(detail, levelColor(detail.level), { selected = null }, Modifier.align(Alignment.Center))
            }
        }
        }
    }
    }
}

/** Car / Truck pills over the hologram, so the vehicle can be switched right on the home screen. */
@Composable
internal fun VehicleSwitch(current: VehicleChoice, onPick: (VehicleChoice) -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (v in VehicleChoice.entries) {
            val on = v == current
            Box(
                Modifier
                    .height(24.dp)
                    .clip(shape)
                    .background(if (on) ScannerRed else ScannerRed.copy(alpha = 0.18f))
                    .skinFrame(shape, ScannerRed, selected = on)
                    .clickable { onPick(v) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(v.label.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (on) KittBlack else DashWhite, maxLines = 1)
            }
        }
    }
}

internal fun levelColor(level: ReadoutLevel): Color = when (level) {
    ReadoutLevel.Unknown -> DashGrey
    ReadoutLevel.Ok -> LedGreen
    ReadoutLevel.Warn -> DashAmber
    ReadoutLevel.Bad -> AlertRed
}

/** A live value as a filled, bold chip; the selected one goes solid. */
@Composable
internal fun ReadoutChip(label: String, value: String, level: ReadoutLevel, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
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
internal fun ReadoutCard(spec: CarReadout, value: Double?, stats: ReadoutStats, metric: Boolean, onClose: () -> Unit, modifier: Modifier) {
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

private fun kindOf(skin: CockpitSkin, choice: VehicleChoice) = HoloVehicle.body(
    choice,
    when (skin) {
        CockpitSkin.Future -> "wedge"
        CockpitSkin.RoboCop -> "cruiser"
        else -> "coupe"
    }
)

private fun readFlipbook(context: android.content.Context, kind: String): Flipbook {
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
    filter: androidx.compose.ui.graphics.ColorFilter,
    blendMode: androidx.compose.ui.graphics.BlendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
) {
    if (b == null || mix <= 0.005f) {
        drawImage(a, dstSize = dst, dstOffset = off, alpha = alpha, colorFilter = filter, blendMode = blendMode)
        return
    }
    val bounds = androidx.compose.ui.geometry.Rect(off.x.toFloat(), off.y.toFloat(), (off.x + dst.width).toFloat(), (off.y + dst.height).toFloat())
    drawContext.canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint().apply {
        this.alpha = alpha
        this.blendMode = blendMode
    })
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
    line: Color, lf: Color, rf: Color, lr: Color, rr: Color, rpm: Float, live: LiveCar, scan: Float,
    callouts: List<Callout>, selected: String?
) {
    // The vehicle sits in the band between the callout rows above and below it, scaled up for maximum presence.
    val boxes = CalloutLayout.boxes(size.width, size.height, 1.dp.toPx())
    val s = minOf((size.width * 1.14f) / book.w, (boxes.bandHeight * 1.28f) / book.h)
    val tx = (size.width - book.w * s) / 2f
    val ty = boxes.bandTop + (boxes.bandHeight - book.h * s) / 2f
    fun p(o: Offset) = Offset(tx + o.x * book.w * s, ty + o.y * book.h * s)
    val perMetre = book.w * s / 5.3f
    val thin = 1.dp.toPx()
    val hot = lerp(line, Color.White, 0.55f)
    val tires = listOf(lf, rf, lr, rr)
    val dst = androidx.compose.ui.unit.IntSize((book.w * s).roundToInt(), (book.h * s).roundToInt())
    val off = androidx.compose.ui.unit.IntOffset(tx.roundToInt(), ty.roundToInt())

    // The stage: a perspective grid on the floor under the vehicle, like the reference.
    drawFloorGrid(line, p(Offset(0f, 0.905f)).y)
    // A squashed, faded mirror image on the floor, like the reflection under the car in the reference.
    val floorY = p(Offset(0f, 0.905f)).y
    withTransform({ scale(1f, -0.32f, Offset(0f, floorY)) }) {
        drawBlended(image, next, mix, dst, off, 0.20f, androidx.compose.ui.graphics.ColorFilter.tint(line, androidx.compose.ui.graphics.BlendMode.SrcIn))
    }
    // The hologram: the glow pass in the theme colour, then an additive pass for the hot cores.
    drawBlended(image, next, mix, dst, off, 1f, androidx.compose.ui.graphics.ColorFilter.tint(line, androidx.compose.ui.graphics.BlendMode.SrcIn))
    drawBlended(image, next, mix, dst, off, 0.45f, androidx.compose.ui.graphics.ColorFilter.tint(hot, androidx.compose.ui.graphics.BlendMode.SrcIn), androidx.compose.ui.graphics.BlendMode.Plus)

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
    if (live.fuel != null) {
        val frac = (live.fuel / 100.0).toFloat().coerceIn(0f, 1f)
        val tw = 34.dp.toPx()
        val th = 6.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.7f), Offset(tank.x - tw / 2, tank.y - th / 2), Size(tw, th))
        drawRect(fuelColor(live.fuel), Offset(tank.x - tw / 2, tank.y - th / 2), Size(tw * frac, th))
        drawRect(fuelColor(live.fuel).copy(alpha = 0.8f), Offset(tank.x - tw / 2, tank.y - th / 2), Size(tw, th), style = Stroke(thin))
    } else {
        drawCircle(fuelColor(null).copy(alpha = 0.25f), 6.dp.toPx(), tank)
    }
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

    fun anchorOf(name: String): Offset = when {
        name == "engine" -> engine
        name == "battery" -> battery
        name == "fuel" -> tank
        name == "roof" -> frame.anchors["roof"]?.let(::p) ?: p(Offset(0.5f, 0.30f))
        name.startsWith("w") -> frame.wheels.firstOrNull { it.slot == name.drop(1).toIntOrNull() }?.let { p(it.hub) } ?: engine
        else -> engine
    }

    // Attention Glow Pass: any live OBD PID area that needs attention (Warn / Bad) or is actively selected
    // emits a pulsing volumetric radial aura and diagnostic ring right on that part of the 3D vehicle.
    val pulseWave = 0.5f + 0.5f * sin(live.pulse * 2f * PI.toFloat())
    for (c in callouts) {
        val needsAttention = c.level == ReadoutLevel.Warn || c.level == ReadoutLevel.Bad
        val isSel = c.key == selected
        if (needsAttention || isSel) {
            val pt = anchorOf(c.anchor)
            val glowCol = if (c.level == ReadoutLevel.Bad) AlertRed else if (c.level == ReadoutLevel.Warn) DashAmber else levelColor(c.level)
            val baseR = when {
                c.anchor == "engine" -> perMetre * 0.62f
                c.anchor.startsWith("w") -> perMetre * 0.48f
                else -> perMetre * 0.42f
            }
            val glowR = baseR * (0.90f + 0.28f * pulseWave)
            val alphaBoost = if (c.level == ReadoutLevel.Bad) 0.62f else if (needsAttention) 0.48f else 0.38f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowCol.copy(alpha = alphaBoost),
                        glowCol.copy(alpha = alphaBoost * 0.40f),
                        Color.Transparent
                    ),
                    center = pt,
                    radius = glowR
                ),
                radius = glowR,
                center = pt
            )
            val ringR = baseR * (0.34f + 0.16f * pulseWave)
            drawCircle(
                color = glowCol.copy(alpha = 0.85f),
                radius = ringR,
                center = pt,
                style = Stroke(width = thin * 1.8f)
            )
        }
    }

    // Live-data callouts: boxes with leader lines to the part each one describes.
    drawCallouts(
        callouts, boxes,
        ::anchorOf,
        line, selected, ::levelColor
    )

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
    val edition = ThemeState.edition
    val panelTitle = when (skin) {
        CockpitSkin.Future -> when (edition) {
            com.aiconsole.kittlauncher2.ui.theme.FilmEdition.HillValley2015 -> "2015 Hover DeLorean // Mr. Fusion"
            com.aiconsole.kittlauncher2.ui.theme.FilmEdition.Frontier1885 -> "1885 Steam Time Machine // Boiler"
            else -> "DeLorean Time Machine // 1.21 GW"
        }
        CockpitSkin.Kitt -> when (ThemeState.theme) {
            com.aiconsole.kittlauncher2.ui.theme.UiTheme.Karr -> "KARR Prototype Status"
            com.aiconsole.kittlauncher2.ui.theme.UiTheme.SuperPursuit -> "Super Pursuit Aerodynamics"
            com.aiconsole.kittlauncher2.ui.theme.UiTheme.NightVision -> "FLIR Thermal Vehicle Scan"
            com.aiconsole.kittlauncher2.ui.theme.UiTheme.RetroVfd -> "1984 VFD Instrument Status"
            com.aiconsole.kittlauncher2.ui.theme.UiTheme.Outrun -> "Outrun Turbo Telemetry"
            com.aiconsole.kittlauncher2.ui.theme.UiTheme.Stealth -> "Stealth Low-Observable HUD"
            else -> skin.hudTitle
        }
        else -> skin.hudTitle
    }
    var showApps by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KittButton("Vehicle HUD", { showApps = false }, Modifier.weight(1f).fillMaxHeight(), DashAmber, active = !showApps)
            KittButton("Apps", { showApps = true }, Modifier.weight(1f).fillMaxHeight(), DashAmber, active = showApps)
        }
        if (showApps) {
            appsContent(Modifier.weight(1f).fillMaxWidth())
        } else {
            KittPanel(title = panelTitle, modifier = Modifier.weight(1f).fillMaxWidth(), accent = DashAmber) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VehicleStatusHud(reading, metric, rpmFraction, Modifier.weight(1f).fillMaxWidth())
                    if (skin == CockpitSkin.Future) {
                        BttfFluxStrip(reading, metric, edition, Modifier.fillMaxWidth().height(36.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BttfFluxStrip(
    reading: (String) -> PidReading?,
    metric: Boolean,
    edition: com.aiconsole.kittlauncher2.ui.theme.FilmEdition?,
    modifier: Modifier = Modifier
) {
    fun fresh(id: String) = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 120_000 }?.value
    val speedKmh = fresh(PidCatalog.SPEED)
    val speedDisplay = speedKmh?.let { if (metric) it.roundToInt() else (it * 0.621371).roundToInt() }
    val coolant = fresh(PidCatalog.COOLANT)?.let { if (metric) it.roundToInt() else (it * 9.0 / 5.0 + 32.0).roundToInt() }
    val volts = fresh(PidCatalog.MODULE_VOLTAGE) ?: fresh(PidCatalog.ADAPTER_VOLTAGE)
    val load = fresh(PidCatalog.ENGINE_LOAD)?.roundToInt()
    val snap = com.aiconsole.kittlauncher2.ui.theme.LiveTelemetrySnapshot(
        speed = speedDisplay,
        speedUnit = if (metric) "KM/H" else "MPH",
        coolantTemp = coolant,
        tempUnit = if (metric) "°C" else "°F",
        volts = volts,
        loadPct = load
    )
    val bttf = com.aiconsole.kittlauncher2.ui.theme.FilmHudText.liveBttfCircuits(snap, edition)
    val shape = MaterialTheme.shapes.extraSmall
    Row(
        modifier
            .clip(shape)
            .background(Color(0xFF0A0A0C))
            .skinFrame(shape, DashAmber)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1.15f)) {
            Text(
                "FLUX: ${bttf.fluxStatus}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (bttf.fluxFraction >= 1f) LedGreen else DashAmber,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                bttf.coreStatus,
                style = MaterialTheme.typography.labelSmall,
                color = DashWhite,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
        LedBarGauge(bttf.fluxFraction, Modifier.weight(0.85f).height(14.dp), segments = 14, color = if (bttf.fluxFraction >= 1f) LedGreen else ScannerRed)
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
    val edition = ThemeState.edition
    val app = LocalContext.current.applicationContext as? KittApplication
    val readings by (app?.obd?.displayReadings ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
    val hz by (app?.obd?.refreshHz ?: kotlinx.coroutines.flow.MutableStateFlow(0f)).collectAsState()
    val dtcs by (app?.obd?.dtcs ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val speaking by (app?.kittVoice?.speaking ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val metric by (app?.settings?.metricUnits?.state ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val lite = LocalLiteMode.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lite) {
        if (lite) return@LaunchedEffect
        while (true) {
            delay(450)
            tick = (tick + 1) and 0x7FFF
        }
    }
    fun fresh(id: String) = readings[id]?.takeIf { System.currentTimeMillis() - it.timestamp < 120_000 }?.value
    val speedKmh = fresh(PidCatalog.SPEED)
    val speedDisp = speedKmh?.let { if (metric) it.roundToInt() else (it * 0.621371).roundToInt() }
    val coolantDisp = fresh(PidCatalog.COOLANT)?.let { if (metric) it.roundToInt() else (it * 9.0 / 5.0 + 32.0).roundToInt() }
    val intakeDisp = fresh(PidCatalog.INTAKE_TEMP)?.let { if (metric) it.roundToInt() else (it * 9.0 / 5.0 + 32.0).roundToInt() }
    val volts = fresh(PidCatalog.MODULE_VOLTAGE) ?: fresh(PidCatalog.ADAPTER_VOLTAGE)
    val snap = com.aiconsole.kittlauncher2.ui.theme.LiveTelemetrySnapshot(
        rpm = fresh(PidCatalog.RPM)?.roundToInt(),
        speed = speedDisp,
        speedUnit = if (metric) "KM/H" else "MPH",
        coolantTemp = coolantDisp,
        tempUnit = if (metric) "°C" else "°F",
        throttlePct = fresh(PidCatalog.THROTTLE)?.roundToInt(),
        loadPct = fresh(PidCatalog.ENGINE_LOAD)?.roundToInt(),
        volts = volts,
        fuelPct = fresh(PidCatalog.FUEL_LEVEL)?.roundToInt(),
        intakeTemp = intakeDisp,
        hz = hz.toDouble(),
        dtcCount = dtcs?.all?.size ?: 0,
        linked = hz > 0f || readings.isNotEmpty(),
        speaking = speaking,
        tick = tick
    )

    val shape = MaterialTheme.shapes.small
    val mono = FontFamily.Monospace
    Box(modifier.clip(shape).background(Color.Black).skinFrame(shape, ScannerRed).padding(8.dp)) {
        when (skin) {
            CockpitSkin.Kitt -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                VoiceModulator(active = speaking, modifier = Modifier.weight(0.95f).fillMaxHeight())
                Column(Modifier.weight(1.45f), verticalArrangement = Arrangement.SpaceEvenly) {
                    val title = when (ThemeState.theme) {
                        com.aiconsole.kittlauncher2.ui.theme.UiTheme.Karr -> "KARR PROTOTYPE CPU"
                        com.aiconsole.kittlauncher2.ui.theme.UiTheme.SuperPursuit -> "KITT // SPM CONSOLE"
                        com.aiconsole.kittlauncher2.ui.theme.UiTheme.NightVision -> "KITT // FLIR RECON"
                        com.aiconsole.kittlauncher2.ui.theme.UiTheme.RetroVfd -> "KNIGHT 2000 // VFD BUS"
                        com.aiconsole.kittlauncher2.ui.theme.UiTheme.Outrun -> "KITT // OUTRUN TURBO"
                        com.aiconsole.kittlauncher2.ui.theme.UiTheme.Stealth -> "KITT // STEALTH MODE"
                        else -> "KNIGHT 2000 CO-PILOT"
                    }
                    Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = ScannerRed, maxLines = 1)
                    Text(
                        if (snap.linked) "OBD LINK: %.1f HZ // ${snap.dtcCount} DTC".format(java.util.Locale.US, snap.hz) else "VOICE CIRCUITS ONLINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (snap.dtcCount > 0) AlertRed else DashAmber,
                        fontFamily = mono,
                        maxLines = 1
                    )
                    Text(
                        "RPM ${snap.rpm ?: "--"} | ${snap.speed ?: "--"} ${snap.speedUnit} | ${snap.volts?.let { "%.1fV".format(java.util.Locale.US, it) } ?: "--V"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DashWhite,
                        fontFamily = mono,
                        maxLines = 1
                    )
                }
            }
            CockpitSkin.Terminator -> {
                val regs = com.aiconsole.kittlauncher2.ui.theme.FilmHudText.cpuRegisters(snap, edition)
                val code = com.aiconsole.kittlauncher2.ui.theme.FilmHudText.liveTerminatorCode(snap, edition)
                val start = 1 + (tick % 6)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    Text(regs, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DashWhite, fontFamily = mono, maxLines = 1)
                    code.drop(start).take(3).forEach {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = mono, maxLines = 1)
                    }
                    Text(code.last(), style = MaterialTheme.typography.labelSmall, color = if (snap.dtcCount > 0) AlertRed else DashAmber, fontFamily = mono, maxLines = 1)
                }
            }
            CockpitSkin.Matrix -> {
                val lines = com.aiconsole.kittlauncher2.ui.theme.FilmHudText.liveMatrixConsole(snap, edition)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    listOf(lines[0], lines[1], lines[2], lines[4], lines[6]).forEachIndexed { idx, line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal),
                            color = if (idx == 0) DashWhite else ScannerRed,
                            fontFamily = mono,
                            maxLines = 1
                        )
                    }
                }
            }
            CockpitSkin.Future -> {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        now = System.currentTimeMillis()
                        kotlinx.coroutines.delay(1_000)
                    }
                }
                val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.US) }
                val bttf = com.aiconsole.kittlauncher2.ui.theme.FilmHudText.liveBttfCircuits(snap, edition)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(
                        Triple("DEST ${bttf.destinationFull.take(11)}", bttf.destinationShort, Color(0xFFFF3B30)),
                        Triple("PRESENT TIME", fmt.format(java.util.Date(now)), LedGreen),
                        Triple("LAST ${bttf.lastDepartedFull.take(11)}", bttf.lastDepartedShort, DashAmber)
                    ).forEach { (label, value, color) ->
                        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, Modifier.weight(1.15f), style = MaterialTheme.typography.labelSmall, color = DashWhite, fontFamily = mono, maxLines = 1)
                            SegmentDisplay(value, Modifier.weight(0.85f).fillMaxHeight(), color, cells = 5)
                        }
                    }
                    Text(
                        bttf.fluxStatus,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (bttf.fluxFraction >= 1f) LedGreen else DashAmber,
                        fontFamily = mono,
                        maxLines = 1
                    )
                }
            }
            CockpitSkin.RoboCop -> {
                val dirs = com.aiconsole.kittlauncher2.ui.theme.FilmHudText.liveRobocopDirectives(edition)
                val vitals = com.aiconsole.kittlauncher2.ui.theme.FilmHudText.liveRobocopTelemetry(snap, edition)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    Text(
                        when (edition) {
                            com.aiconsole.kittlauncher2.ui.theme.FilmEdition.Ed209 -> "ED-209 PACIFICATION"
                            com.aiconsole.kittlauncher2.ui.theme.FilmEdition.DeltaCity -> "DELTA CITY DIRECTIVES"
                            else -> "OCP PRIME DIRECTIVES"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = DashYellow,
                        fontFamily = mono,
                        maxLines = 1
                    )
                    dirs.take(3).forEach { (n, text) ->
                        Text("$n. $text", style = MaterialTheme.typography.labelSmall, color = ScannerRed, fontFamily = mono, maxLines = 1)
                    }
                    Text(
                        "${vitals[1].second} // ${vitals[3].second}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = DashWhite,
                        fontFamily = mono,
                        maxLines = 1
                    )
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
