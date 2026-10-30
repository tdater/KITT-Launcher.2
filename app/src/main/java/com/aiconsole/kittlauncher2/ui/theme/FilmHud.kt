package com.aiconsole.kittlauncher2.ui.theme

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.BuildConfig
import com.aiconsole.kittlauncher2.browser.WebMedia
import kotlin.random.Random

/** Text the film HUD themes show in the status strip, panels, screensaver and intros (see [FilmEdition]). */
object FilmHudText {

    fun lines(edition: FilmEdition): List<String> =
        if (BuildConfig.HOLO_DESIGN) edition.lines + FilmQuotes.forFilm(edition.film) else edition.lines

    /** The film's original edition's readouts. */
    fun lines(style: HudStyle): List<String> = lines(FilmEdition.default(style))

    /** Brand line for the status strip. */
    fun brand(edition: FilmEdition): String = edition.brand

    /** What the alert banner calls KITT's messages. */
    fun alertTitle(edition: FilmEdition?): String? = edition?.alertTitle

    /** The boot sequence each edition shows when it comes on: original lines, not dialogue (the Holo edition's strip also carries film lines). */
    fun intro(edition: FilmEdition): List<String> = edition.intro

    fun intro(style: HudStyle): List<String> = intro(FilmEdition.default(style))

    /** Machine-looking tag for a panel title, e.g. "0x5E2A": stable per title. */
    fun hexTag(title: String): String = "0x%04X".format(title.uppercase().hashCode() and 0xFFFF)

    /** The T-800 vision's scrolling machine code: fallback static seed plus live OBD/telemetry generators below. */
    val MACHINE_CODE: List<String> = Random(1984).let { r ->
        val ops = listOf("LDA", "STA", "JSR", "CMP", "BNE", "LDX", "INX", "RTS", "AND", "ORA")
        val words = listOf("SCAN", "VEHICLE.SYS", "TARGET", "ROUTE", "THERMAL", "AUDIO", "MOTOR", "VISION", "PURSUIT", "FUEL")
        List(48) { i ->
            when (i % 4) {
                0 -> "%04X %s #$%02X".format(0x0400 + i * 6, ops[r.nextInt(ops.size)], r.nextInt(256))
                1 -> "%s.%02d %s".format(words[r.nextInt(words.size)], r.nextInt(100), if (r.nextBoolean()) "OK" else "...")
                2 -> "MATCH %d.%d%%".format(80 + r.nextInt(20), r.nextInt(10))
                else -> List(4) { "%02X".format(r.nextInt(256)) }.joinToString(" ")
            }
        }
    }

    /** Live 6502 / Cyberdyne CPU register banner derived from real-time vehicle sensors. */
    fun cpuRegisters(snap: LiveTelemetrySnapshot, edition: FilmEdition? = FilmEdition.T800): String {
        val pc = 0x0300 + ((snap.tick and 0x0F) * 2)
        val a = ((snap.rpm ?: 0) / 32).coerceIn(0, 255)
        val x = (snap.speed ?: 0).coerceIn(0, 255)
        val y = ((snap.coolantTemp ?: 0) and 0xFF)
        val n = if (snap.overLimit) '1' else '0'
        val v = if (snap.dtcCount > 0) '1' else '0'
        val b = if (snap.speaking) '1' else '0'
        val d = if (edition == FilmEdition.T1000) '1' else '0'
        val i = if (!snap.linked) '1' else '0'
        val z = if ((snap.rpm ?: 0) == 0) '1' else '0'
        val c = if ((snap.volts ?: 0.0) >= 13.2) '1' else '0'
        return java.util.Locale.US.let { loc ->
            "PC:$%04X A:$%02X X:$%02X Y:$%02X SR:%c%c-%c%c%c%c%c".format(loc, pc, a, x, y, n, v, b, d, i, z, c)
        }
    }

    /**
     * Live 6502 / Cyberdyne / Polyalloy / Skynet machine-code disassembly driven by real-time OBD & GPS telemetry.
     * Every instruction's hex opcode/operand encodes the live vehicle state and updates as the sensors change.
     */
    fun liveTerminatorCode(snap: LiveTelemetrySnapshot, edition: FilmEdition? = FilmEdition.T800): List<String> {
        val loc = java.util.Locale.US
        val rpmVal = snap.rpm ?: 0
        val rpmByte = (rpmVal / 32).coerceIn(0, 255)
        val spdVal = snap.speed ?: 0
        val spdByte = spdVal.coerceIn(0, 255)
        val limByte = (snap.speedLimit ?: 0).coerceIn(0, 255)
        val ectVal = snap.coolantTemp ?: 0
        val ectByte = ectVal.coerceIn(0, 255)
        val tpsVal = snap.throttlePct ?: 0
        val tpsByte = (tpsVal * 255 / 100).coerceIn(0, 255)
        val loadVal = snap.loadPct ?: 0
        val loadByte = (loadVal * 255 / 100).coerceIn(0, 255)
        val vInt = ((snap.volts ?: 0.0) * 10.0).toInt().coerceIn(0, 255)
        val fuelVal = snap.fuelPct ?: 0
        val dtcByte = snap.dtcCount.coerceIn(0, 255)

        val rpmStr = snap.rpm?.toString() ?: "--"
        val spdStr = snap.speed?.toString() ?: "--"
        val limStr = snap.speedLimit?.toString() ?: "--"
        val ectStr = snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "--"
        val tpsStr = snap.throttlePct?.let { "$it%" } ?: "--"
        val loadStr = snap.loadPct?.let { "$it%" } ?: "--"
        val voltStr = snap.volts?.let { "%.1fV".format(loc, it) } ?: "--"
        val fuelStr = snap.fuelPct?.let { "$it%" } ?: "--"
        val busStr = if (snap.linked) "%.1fHZ".format(loc, snap.hz) else "OFFLINE"

        val header = when (edition) {
            FilmEdition.T1000 -> "; MIMETIC POLYALLOY // M-1000 HEX"
            FilmEdition.FutureWar -> "; SKYNET HK-NET // TACTICAL CORE"
            else -> "; CYBERDYNE 6502 // CSM-101 LIVE"
        }
        val subVec = when (edition) {
            FilmEdition.T1000 -> "0317: 20 A4 D0 JSR \$D0A4 ; MORPH_SYNC"
            FilmEdition.FutureWar -> "0317: 20 F0 E8 JSR \$E8F0 ; HK_RADAR"
            else -> "0317: 20 88 C0 JSR \$C088 ; IR_TARGET"
        }
        val baseAddr = 0xC000 + ((snap.tick and 0x1F) * 8)
        val b0 = (rpmByte xor (snap.tick and 0xFF)) and 0xFF
        val b1 = spdByte
        val b2 = ectByte
        val b3 = tpsByte
        val b4 = loadByte
        val b5 = vInt
        val hexRow1 = "%04X: %02X %02X %02X %02X %02X %02X  %s".format(
            loc, baseAddr, b0, b1, b2, b3, b4, b5, if (snap.linked) "BUS.OK" else "NO.BUS"
        )
        val hexRow2 = "%04X: %02X %02X %02X %02X %02X %02X  %s".format(
            loc, baseAddr + 8, b5, fuelVal and 0xFF, dtcByte, (snap.tick * 13) and 0xFF, (rpmVal and 0xFF), (spdByte xor 0x5A),
            if (snap.speaking) "VOX.ON" else "VOX.STBY"
        )
        val threat = when {
            snap.dtcCount > 0 -> "FAULT: ${snap.dtcCount} DTC ACTIVE"
            snap.overLimit -> "ALERT: VELOCITY > LIMIT"
            snap.linked -> "NOMINAL // TARGET LOCKED"
            else -> "PASSIVE // AWAITING OBD"
        }
        return listOf(
            header,
            "0300: A9 %02X    LDA #$%02X  ; RPM=%s".format(loc, rpmByte, rpmByte, rpmStr),
            "0302: 8D 0C E0 STA \$E00C ; TACH_REG",
            "0305: A2 %02X    LDX #$%02X  ; VEL=%s%s".format(loc, spdByte, spdByte, spdStr, snap.speedUnit),
            "0307: E0 %02X    CPX #$%02X  ; LIM=%s".format(loc, limByte, limByte, limStr),
            "0309: A0 %02X    LDY #$%02X  ; ECT=%s".format(loc, ectByte, ectByte, ectStr),
            "030B: 8C 05 E0 STY \$E005 ; THERM_CORE",
            "030E: A9 %02X    LDA #$%02X  ; TPS=%s LD=%s".format(loc, tpsByte, tpsByte, tpsStr, loadStr),
            "0310: 69 %02X    ADC #$%02X  ; V=%s FL=%s".format(loc, vInt, vInt, voltStr, fuelStr),
            "0312: 20 40 F8 JSR \$F840 ; OBD=%s".format(loc, busStr),
            "0315: C9 %02X    CMP #$%02X  ; DTC=%d".format(loc, dtcByte, dtcByte, snap.dtcCount),
            subVec,
            hexRow1,
            hexRow2,
            "SCAN.VEH : ${if (snap.linked) "ONLINE ($busStr)" else "STANDBY / NO LINK"}",
            "THREAT   : $threat"
        )
    }

    /** Live Matrix glyph & hex wave decoder string encoding real-time vehicle sensor bytes. */
    fun liveMatrixGlyphStream(snap: LiveTelemetrySnapshot): String {
        val loc = java.util.Locale.US
        val glyphs = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ"
        val rpm = snap.rpm ?: 0
        val spd = snap.speed ?: 0
        val ect = snap.coolantTemp ?: 0
        val tps = snap.throttlePct ?: 0
        val vInt = ((snap.volts ?: 0.0) * 10.0).toInt().coerceIn(0, 255)
        val t = snap.tick
        fun g(seed: Int, count: Int): String = buildString {
            for (i in 0 until count) {
                append(glyphs[kotlin.math.abs(seed + i * 7 + t * 3) % glyphs.length])
            }
        }
        val binRpm = Integer.toBinaryString((rpm / 64).coerceIn(0, 255)).padStart(8, '0')
        val binSpd = Integer.toBinaryString(spd.coerceIn(0, 255)).padStart(8, '0')
        return "%02X %02X %s %s | %04dRPM %03d%s | %s %02X:%02X %s %02X".format(
            loc,
            (rpm / 32) and 0xFF,
            spd and 0xFF,
            binRpm.take(4),
            g(rpm + spd, 6),
            rpm,
            spd,
            snap.speedUnit,
            binSpd.take(4),
            ect and 0xFF,
            tps and 0xFF,
            g(ect + vInt + 11, 5),
            vInt
        )
    }

    /** Live VT220 Operator console stream for The Matrix (Code, RealWorld, Zion). */
    fun liveMatrixConsole(snap: LiveTelemetrySnapshot, edition: FilmEdition? = FilmEdition.Code): List<String> {
        val loc = java.util.Locale.US
        val header = when (edition) {
            FilmEdition.RealWorld -> "HOVERCRAFT MJORLNIR // EM CONSOLE"
            FilmEdition.Zion -> "ZION MAINFRAME // DOCK DEFENSE"
            else -> "NEBUCHADNEZZAR // OPERATOR VT220"
        }
        val bus = if (snap.linked) "LOCKED @ %.1fHz".format(loc, snap.hz) else "CARRIER LOST (SCANNING)"
        val rpmStr = snap.rpm?.toString() ?: "--"
        val spdStr = snap.speed?.let { "$it ${snap.speedUnit}" } ?: "-- ${snap.speedUnit}"
        val radar = snap.speedLimit?.let { "LIMIT $it ${snap.speedUnit}${if (snap.overLimit) " [AGENT ALERT]" else " [CLEAR]"}" } ?: "SWEEPING SECTOR"
        val ectStr = snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "--"
        val iatStr = snap.intakeTemp?.let { "$it${snap.tempUnit}" } ?: "--"
        val tpsStr = snap.throttlePct?.let { "$it%" } ?: "--"
        val loadStr = snap.loadPct?.let { "$it%" } ?: "--"
        val voltStr = snap.volts?.let { "%.1fV".format(loc, it) } ?: "--"
        val fuelStr = snap.fuelPct?.let { "$it%" } ?: "--"
        val subLine = when (edition) {
            FilmEdition.RealWorld -> "> hover.pads : COIL LOAD $loadStr // HULL OK"
            FilmEdition.Zion -> "> dock.gates : SECTOR 04 // APU $voltStr"
            else -> "> construct  : CODE STREAM SYNCED"
        }
        val sentinel = if (snap.dtcCount > 0) "ALERT: ${snap.dtcCount} DTC ANOMALY" else "CLEAR (0 ANOMALIES)"
        val addr = 0x7F00 + ((snap.tick and 0x0F) * 4)
        val b0 = ((snap.rpm ?: 0) / 32) and 0xFF
        val b1 = (snap.speed ?: 0) and 0xFF
        val b2 = (snap.coolantTemp ?: 0) and 0xFF
        val b3 = (snap.throttlePct ?: 0) and 0xFF
        val cursor = if (snap.tick % 2 == 0) "█" else "_"
        return listOf(
            header,
            "> obd.link   : $bus",
            "> ecu.vector : RPM=$rpmStr VEL=$spdStr",
            "> nav.radar  : $radar",
            "> core.therm : ECT=$ectStr IAT=$iatStr",
            "> drive.load : TPS=$tpsStr LOAD=$loadStr",
            "> emp.cell   : BUS=$voltStr FUEL=$fuelStr",
            "> sentinel   : $sentinel",
            subLine,
            "> mem.0x%04X : %02X %02X %02X %02X [SYNC]".format(loc, addr, b0, b1, b2, b3),
            "> comms.line : ${if (snap.speaking) "OPERATOR TRANSMITTING" else "HARDLINE READY"}",
            ">$cursor"
        )
    }

    /** Edition-aware RoboCop Directives (OCP Visor, ED-209 Urban Pacification, Delta City Executive). */
    fun liveRobocopDirectives(edition: FilmEdition? = FilmEdition.Visor): List<Pair<String, String>> = when (edition) {
        FilmEdition.Ed209 -> listOf(
            "1" to "PACIFY DESIGNATED SECTOR",
            "2" to "DISARM HOSTILE SUBJECTS",
            "3" to "20-SECOND COMPLIANCE WINDOW",
            "4" to "[AUTHORIZED LETHAL FORCE]"
        )
        FilmEdition.DeltaCity -> listOf(
            "1" to "SECURE DELTA CITY GRID",
            "2" to "PROTECT OCP EXECUTIVES",
            "3" to "ENFORCE TRAFFIC STATUTES",
            "4" to "[DIRECTIVE 4: EXECUTIVE LOCK]"
        )
        else -> listOf(
            "1" to "SERVE THE PUBLIC TRUST",
            "2" to "PROTECT THE INNOCENT",
            "3" to "UPHOLD THE LAW",
            "4" to "[CLASSIFIED // OCP EXEC]"
        )
    }

    /** Live OCP Patrol telemetry rows displayed below the Prime Directives in RoboCop Drive & SkinTerminal. */
    fun liveRobocopTelemetry(snap: LiveTelemetrySnapshot, edition: FilmEdition? = FilmEdition.Visor): List<Pair<String, String>> {
        val loc = java.util.Locale.US
        val vel = snap.speed?.let { "$it ${snap.speedUnit}" } ?: "-- ${snap.speedUnit}"
        val lim = snap.speedLimit?.let { "LIM $it" } ?: "PATROL"
        val rpm = snap.rpm?.let { "$it RPM" } ?: "-- RPM"
        val ect = snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "--"
        val load = snap.loadPct?.let { "$it%" } ?: "--"
        val tps = snap.throttlePct?.let { "$it%" } ?: "--"
        val volts = snap.volts?.let { "%.1fV".format(loc, it) } ?: "--"
        val dtc = if (snap.dtcCount > 0) "${snap.dtcCount} FAULT(S)" else "CLEAR"
        val unitTag = when (edition) {
            FilmEdition.Ed209 -> "ED-209 // CHASSIS"
            FilmEdition.DeltaCity -> "OCP ONE // EXEC"
            else -> "UNIT 001 // CRUISER"
        }
        return listOf(
            "UNIT ID" to "$unitTag (${if (snap.linked) "%.1fHZ".format(loc, snap.hz) else "OFFLINE"})",
            "VELOCITY" to "$vel [$lim]",
            "TURBINE" to "$rpm // TPS $tps",
            "THERMAL" to "ECT $ect // LOAD $load",
            "ALTERNATOR" to "$volts [${if ((snap.volts ?: 0.0) >= 13.2) "CHARGING" else "STANDBY"}]",
            "VIOLATIONS" to "ECU DTC: $dtc"
        )
    }

    /**
     * Authentic Back to the Future Time Circuits & Flux/Propulsion telemetry for 1985 DeLorean,
     * 2015 Hill Valley (Mr. Fusion), and 1885 Frontier (Steam Locomotive Boiler).
     */
    fun liveBttfCircuits(snap: LiveTelemetrySnapshot, edition: FilmEdition? = FilmEdition.Delorean1985): BttfCircuitReadout {
        val loc = java.util.Locale.US
        val destTime = when (edition) {
            FilmEdition.HillValley2015 -> "OCT 21 2015 16:29"
            FilmEdition.Frontier1885 -> "SEP 02 1885 08:00"
            else -> "OCT 26 1985 01:21"
        }
        val destShort = when (edition) {
            FilmEdition.HillValley2015 -> "16:29"
            FilmEdition.Frontier1885 -> "08:00"
            else -> "01:21"
        }
        val lastDeparted = when (edition) {
            FilmEdition.HillValley2015 -> "OCT 26 1985 10:28"
            FilmEdition.Frontier1885 -> "NOV 12 1955 09:00"
            else -> "NOV 05 1955 06:00"
        }
        val lastShort = when (edition) {
            FilmEdition.HillValley2015 -> "10:28"
            FilmEdition.Frontier1885 -> "09:00"
            else -> "06:00"
        }
        val targetSpeed = if (snap.speedUnit.uppercase().contains("KM")) 142 else 88
        val curSpeed = snap.speed ?: 0
        val fluxPct = (curSpeed.toFloat() / targetSpeed.toFloat()).coerceIn(0f, 1f)
        val gw = if (curSpeed >= targetSpeed) 1.21 else (fluxPct * 1.21)
        val fluxLabel = if (curSpeed >= targetSpeed) "1.21 GW // TIME DISPLACEMENT" else "%.2f / 1.21 GW (%d/%d %s)".format(loc, gw, curSpeed, targetSpeed, snap.speedUnit)
        val coreLabel = when (edition) {
            FilmEdition.HillValley2015 -> "MR. FUSION: ${snap.volts?.let { "%.1fV".format(loc, it) } ?: "ONLINE"} // HOVER: ${snap.loadPct?.let { "$it%" } ?: "STBY"}"
            FilmEdition.Frontier1885 -> {
                val logStage = when {
                    (snap.coolantTemp ?: 0) >= 105 -> "RED LOG (MAX STEAM)"
                    (snap.coolantTemp ?: 0) >= 85 -> "YELLOW LOG (FIRING)"
                    else -> "GREEN LOG (${snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "WARMING"})"
                }
                "BOILER: $logStage"
            }
            else -> "CHAMBER: ${snap.coolantTemp?.let { "$it${snap.tempUnit}" } ?: "NOMINAL"} // BUS: ${snap.volts?.let { "%.1fV".format(loc, it) } ?: "--"}"
        }
        return BttfCircuitReadout(
            destinationFull = destTime,
            destinationShort = destShort,
            lastDepartedFull = lastDeparted,
            lastDepartedShort = lastShort,
            fluxStatus = fluxLabel,
            coreStatus = coreLabel,
            fluxFraction = fluxPct
        )
    }
}

data class LiveTelemetrySnapshot(
    val rpm: Int? = null,
    val speed: Int? = null,
    val speedUnit: String = "MPH",
    val coolantTemp: Int? = null,
    val tempUnit: String = "°F",
    val throttlePct: Int? = null,
    val loadPct: Int? = null,
    val volts: Double? = null,
    val fuelPct: Int? = null,
    val intakeTemp: Int? = null,
    val hz: Double = 0.0,
    val dtcCount: Int = 0,
    val linked: Boolean = false,
    val speedLimit: Int? = null,
    val overLimit: Boolean = false,
    val speaking: Boolean = false,
    val tick: Int = 0
)

data class BttfCircuitReadout(
    val destinationFull: String,
    val destinationShort: String,
    val lastDepartedFull: String,
    val lastDepartedShort: String,
    val fluxStatus: String,
    val coreStatus: String,
    val fluxFraction: Float
)

/** Which editions' overlays move, and how often they redraw (nanoseconds per frame). */
private fun overlayStep(edition: FilmEdition): Long? = when (edition) {
    FilmEdition.T800 -> 80_000_000L // ~12 fps for the code column.
    FilmEdition.Visor -> 33_000_000L // ~30 fps for the visor sweep.
    FilmEdition.T1000, FilmEdition.FutureWar -> 50_000_000L // Slow sheen and searchlight: 20 fps reads as smooth.
    else -> null
}

/**
 * Full-screen HUD layer drawn over the launcher (never intercepts touches), per edition:
 * - Terminator: red vision with scrolling machine code (T-800); a liquid-chrome sheen flowing
 *   across a steel vignette (Liquid Metal); a hunter-killer searchlight over night-battle blue
 *   (Future War);
 * - RoboCop: visor glow with a sweeping scan band (Visor); a hazard-stripe band and red sensor
 *   glow (ED-209); executive gold light top and bottom (Delta City);
 * - The Matrix: green CRT bloom (Digital Rain), the hovercraft's cold blue monitors (Real World),
 *   Zion's amber firelight;
 * - Back to the Future: brushed-steel edges (1985), neon magenta and cyan (2015), sepia (1885).
 * Moving parts stand still in lite mode and while a web page is on screen.
 */
@Composable
fun FilmHudOverlay(edition: FilmEdition, modifier: Modifier = Modifier) {
    val step = overlayStep(edition)
    // Still over a web page too: this layer covers the whole screen, so every frame of it made
    // the page underneath composite again.
    val animate = step != null && !LocalLiteMode.current && WebMedia.pagesOnScreen == 0
    var now by remember { mutableLongStateOf(0L) }
    if (animate) {
        LaunchedEffect(edition) {
            var last = 0L
            while (true) withFrameNanos {
                if (it - last >= step!!) {
                    last = it
                    now = it
                }
            }
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    Canvas(modifier) {
        if (edition.film != HudStyle.Future && edition != FilmEdition.Zion) scanlines(if (edition.film == HudStyle.Terminator) 0.22f else 0.16f)
        fun vignette(color: Color, reach: Float = 0.75f) =
            drawRect(Brush.radialGradient(listOf(Color.Transparent, color), center = center, radius = size.maxDimension * reach))
        when (edition) {
            FilmEdition.T800 -> {
                vignette(Color(0x55FF1A0A))
                if (!FilmHudState.driveShowsCode) machineCode(paint, now)
            }
            FilmEdition.T1000 -> {
                vignette(Color(0x40A8C0D8), 0.8f)
                chromeSheen(now)
            }
            FilmEdition.FutureWar -> {
                vignette(Color(0x55101E66))
                searchlight(now)
            }
            FilmEdition.Visor -> {
                edgeGlow(Color(0x3366CCFF))
                visorSweep(now)
            }
            FilmEdition.Ed209 -> {
                vignette(Color(0x33FF2A1A), 0.85f)
                hazardBand()
            }
            FilmEdition.DeltaCity -> edgeGlow(Color(0x30E8C45A))
            // Green phosphor bloom at the edges of an old monochrome CRT.
            FilmEdition.Code -> vignette(Color(0x3300FF41), 0.8f)
            FilmEdition.RealWorld -> vignette(Color(0x2E5FD4FF), 0.8f)
            FilmEdition.Zion -> vignette(Color(0x40FF8A1E), 0.7f)
            FilmEdition.Delorean1985 -> {
                // Brushed-steel sheen along the edges, like the DeLorean's panels.
                sideGlow(Color(0x22C8D0D8), Color(0x22C8D0D8))
            }
            FilmEdition.HillValley2015 -> sideGlow(Color(0x2EFF3DB8), Color(0x2E3DF0FF))
            FilmEdition.Frontier1885 -> vignette(Color(0x66402810), 0.7f)
        }
    }
}

private fun DrawScope.scanlines(alpha: Float) {
    val pitch = 3.dp.toPx()
    var y = 0f
    val line = Color.Black.copy(alpha = alpha)
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += pitch
    }
}

/** Light along the top and bottom edges (a visor's rim, a boardroom's downlights). */
private fun DrawScope.edgeGlow(glow: Color) {
    drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), startY = 0f, endY = size.height * 0.12f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = size.height * 0.88f, endY = size.height))
}

private fun DrawScope.sideGlow(left: Color, right: Color) {
    drawRect(Brush.horizontalGradient(listOf(left, Color.Transparent), startX = 0f, endX = size.width * 0.06f))
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, right), startX = size.width * 0.94f, endX = size.width))
}

/** Liquid metal: a soft diagonal band of chrome light flows across the screen now and then. */
private fun DrawScope.chromeSheen(now: Long) {
    if (now == 0L) return
    val period = 9_000_000_000L
    val flow = 2_200_000_000L
    val t = now % period
    if (t > flow) return
    val p = t.toFloat() / flow
    val x = -size.width * 0.3f + size.width * 1.6f * p
    val band = size.width * 0.22f
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color(0x22DCE8F2), Color(0x44FFFFFF), Color(0x22DCE8F2), Color.Transparent),
            start = Offset(x - band, 0f),
            end = Offset(x + band, size.height * 0.6f)
        )
    )
}

/** Future war: a hunter-killer's searchlight drifts across the top of the screen. */
private fun DrawScope.searchlight(now: Long) {
    if (now == 0L) return
    val period = 14_000_000_000L
    val phase = (now % period).toDouble() / period
    val x = size.width * (0.5f + 0.42f * kotlin.math.sin(2 * Math.PI * phase).toFloat())
    val y = size.height * (0.22f + 0.08f * kotlin.math.cos(4 * Math.PI * phase).toFloat())
    val r = size.minDimension * 0.42f
    drawCircle(Brush.radialGradient(listOf(Color(0x2AC8D4FF), Color(0x10C8D4FF), Color.Transparent), center = Offset(x, y), radius = r), r, Offset(x, y))
}

/** ED-209: a thin yellow-and-black hazard band along the bottom edge. */
private fun DrawScope.hazardBand() {
    val h = 4.dp.toPx()
    val top = size.height - h
    val stripe = 10.dp.toPx()
    drawRect(Color(0x99000000), Offset(0f, top), Size(size.width, h))
    clipRect(0f, top, size.width, size.height) {
        var x = -h
        while (x < size.width + h) {
            drawLine(Color(0x99FFC400), Offset(x, size.height), Offset(x + h, top), stripe / 2)
            x += stripe
        }
    }
}

/** Set by a Drive page that already shows the machine code in its own panel, so the margin copy steps aside. */
object FilmHudState {
    var driveShowsCode by mutableStateOf(false)
    var latestSnapshot by mutableStateOf(LiveTelemetrySnapshot())
}

/** The T-800's column of scrolling code down the right edge, driven by live telemetry + assembly dump. */
private fun DrawScope.machineCode(paint: Paint, now: Long) {
    val lines = FilmHudText.liveTerminatorCode(FilmHudState.latestSnapshot, FilmEdition.T800) + FilmHudText.MACHINE_CODE.take(16)
    if (BuildConfig.HOLO_DESIGN) {
        // Dim, small and flush to the right edge so it reads as a margin readout and never fights
        // with the text of the page underneath.
        paint.textSize = 8.dp.toPx()
        paint.color = Color(0xFFFF4A3A).copy(alpha = 0.28f).toArgb()
        paint.textAlign = Paint.Align.RIGHT
    } else {
        paint.textSize = 9.dp.toPx()
        paint.color = Color(0xFFFF4A3A).copy(alpha = 0.55f).toArgb()
    }
    val lineHeight = paint.textSize * 1.35f
    val shown = (size.height * 0.5f / lineHeight).toInt().coerceAtLeast(1)
    val offset = ((now / 160_000_000L) % lines.size).toInt()
    val x = if (BuildConfig.HOLO_DESIGN) size.width - 4.dp.toPx() else size.width - paint.textSize * 11
    val top = size.height * 0.25f
    drawIntoCanvas { canvas ->
        for (i in 0 until shown) {
            canvas.nativeCanvas.drawText(lines[(offset + i) % lines.size], x, top + i * lineHeight, paint)
        }
    }
}

/** RoboCop's visor scan: a bright band sweeps down the screen every few seconds. */
private fun DrawScope.visorSweep(now: Long) {
    if (now == 0L) return
    val period = 7_000_000_000L
    val sweep = 1_400_000_000L
    val t = now % period
    if (t > sweep) return
    val y = size.height * (t.toFloat() / sweep)
    val band = 26.dp.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x4066CCFF), Color(0x9966CCFF), Color.Transparent), startY = y - band, endY = y + band / 3),
        topLeft = Offset(0f, y - band),
        size = Size(size.width, band * 1.4f)
    )
}

/**
 * Panel frame marks: RoboCop draws targeting brackets on the four corners; Terminator draws
 * short corner ticks like the T-800's framing marks; Back to the Future puts a steel rivet in
 * each corner like the DeLorean's bolted-on hardware.
 */
fun Modifier.hudFrame(style: HudStyle?, accent: Color): Modifier = when (style) {
    null, HudStyle.Matrix -> this
    HudStyle.Future -> drawWithContent {
        drawContent()
        val r = 3.dp.toPx()
        val inset = 7.dp.toPx()
        listOf(
            Offset(inset, inset), Offset(size.width - inset, inset),
            Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset)
        ).forEach { c ->
            drawCircle(Color(0xFF9AA2AA), r, c)
            drawCircle(Color(0xFF3A3F45), r * 0.45f, c)
        }
    }
    else -> drawWithContent {
        drawContent()
        val len = (if (style == HudStyle.RoboCop) 18.dp else 10.dp).toPx()
        val inset = 3.dp.toPx()
        val w = (if (style == HudStyle.RoboCop) 2.dp else 1.5.dp).toPx()
        val c = accent.copy(alpha = 0.9f)
        val l = inset
        val t = inset
        val r = size.width - inset
        val b = size.height - inset
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * len, y), w, StrokeCap.Square)
            drawLine(c, Offset(x, y), Offset(x, y + dy * len), w, StrokeCap.Square)
        }
        corner(l, t, 1f, 1f)
        corner(r, t, -1f, 1f)
        corner(l, b, 1f, -1f)
        corner(r, b, -1f, -1f)
    }
}

/** Holo edition only: famous lines from each film, shown in its status strip and screensaver among the system readouts. */
object FilmQuotes {
    fun forFilm(film: HudStyle): List<String> = when (film) {
        HudStyle.Terminator -> listOf(
            "I'LL BE BACK", "COME WITH ME IF YOU WANT TO LIVE", "HASTA LA VISTA, BABY", "NO PROBLEMO",
            "THE FUTURE'S NOT SET", "THERE'S NO FATE BUT WHAT WE MAKE FOR OURSELVES", "AFFIRMATIVE"
        )
        HudStyle.Matrix -> listOf(
            "WAKE UP, NEO...", "FOLLOW THE WHITE RABBIT", "THERE IS NO SPOON", "FREE YOUR MIND",
            "I KNOW KUNG FU", "WELCOME TO THE REAL WORLD", "WHAT IS THE MATRIX?", "NEVER SEND A HUMAN TO DO A MACHINE'S JOB"
        )
        HudStyle.Future -> listOf(
            "GREAT SCOTT!", "ROADS? WHERE WE'RE GOING, WE DON'T NEED ROADS", "1.21 GIGAWATTS!", "THIS IS HEAVY",
            "YOUR FUTURE HASN'T BEEN WRITTEN YET", "IF YOU PUT YOUR MIND TO IT, YOU CAN ACCOMPLISH ANYTHING", "88 MILES PER HOUR"
        )
        HudStyle.RoboCop -> listOf(
            "DEAD OR ALIVE, YOU'RE COMING WITH ME", "YOUR MOVE, CREEP", "I'D BUY THAT FOR A DOLLAR!",
            "THANK YOU FOR YOUR COOPERATION", "YOU HAVE 20 SECONDS TO COMPLY", "SERVE THE PUBLIC TRUST", "PROTECT THE INNOCENT", "UPHOLD THE LAW"
        )
    }
}
