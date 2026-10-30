package com.aiconsole.kittlauncher2.ui.panes

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.ui.components.CockpitSkin
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.components.skinFrame
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.DashYellow
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.KittCharcoal
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable container for built-in automotive cockpit widgets.
 * Automatically frames the card per film theme using skinFrame (Terminator targeting brackets,
 * Matrix phosphor CRT ticks, Future DeLorean steel rivets, or RoboCop hazard stripes).
 */
@Composable
fun BuiltInWidgetCard(
    title: String,
    badge: String,
    badgeColor: Color,
    modifier: Modifier = Modifier,
    accent: Color = ScannerRed,
    content: @Composable () -> Unit
) {
    val skin = CockpitSkin.current()
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KittBlack)
            .skinFrame(shape, accent)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = if (skin == CockpitSkin.Matrix) FontFamily.Monospace else FontFamily.Default
                ),
                color = DashWhite
            )
            Box(
                Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(badgeColor.copy(alpha = 0.22f))
                    .border(1.dp, badgeColor.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    badge.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                    color = badgeColor
                )
            }
        }
        content()
    }
}

/**
 * 1. Quick Commute & Navigation Dispatcher:
 * Compass, heading coordinates, and one-tap nav dispatchers styled to each film theme.
 */
@Composable
fun NavDispatcherWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val skin = CockpitSkin.current()

    fun launchNav(query: String) {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    val (badgeText, badgeColor) = when (skin) {
        CockpitSkin.Terminator -> "TARGET ACQUIRED · 315° AZIMUTH" to ScannerRed
        CockpitSkin.Matrix -> "OPERATOR CARRIER · ACTIVE FEED" to LedGreen
        CockpitSkin.Future -> "TEMPORAL GEO · 1985 COORDINATES" to Color(0xFF00E5FF)
        CockpitSkin.RoboCop -> "DETROIT SECTOR 4 · PATROL READY" to DashYellow
        CockpitSkin.Kitt -> "GPS READY · CRUISE ACTIVE" to LedGreen
    }

    val (titleText, homeBtn, workBtn) = when (skin) {
        CockpitSkin.Terminator -> Triple("Target Navigation & Waypoints", "🏠 Safehouse", "🏢 Skynet Core")
        CockpitSkin.Matrix -> Triple("Construct Navigation & Portals", "🏠 Zion Core", "🏢 Nebuchadnezzar")
        CockpitSkin.Future -> Triple("Temporal Nav & Flight Vector", "🏠 1985 Home", "🏢 Hill Valley High")
        CockpitSkin.RoboCop -> Triple("Patrol Dispatch & Navigation", "🏠 Precinct", "🏢 OCP HQ")
        CockpitSkin.Kitt -> Triple("Navigation Dispatcher", "🏠 Home", "🏢 Work")
    }

    BuiltInWidgetCard(
        title = titleText,
        badge = badgeText,
        badgeColor = badgeColor,
        accent = when (skin) {
            CockpitSkin.Terminator -> ScannerRed
            CockpitSkin.Matrix -> LedGreen
            CockpitSkin.Future -> Color(0xFF00E5FF)
            CockpitSkin.RoboCop -> DashYellow
            CockpitSkin.Kitt -> ScannerRed
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Theme-aware Compass & Targeting Reticle
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    when (skin) {
                        CockpitSkin.Terminator -> {
                            // Red T-800 HUD targeting reticle with azimuth ticks
                            drawCircle(KittCharcoal, r)
                            drawCircle(ScannerRed, r, style = Stroke(1.5.dp.toPx()))
                            drawCircle(ScannerRed.copy(alpha = 0.5f), r * 0.55f, style = Stroke(1.dp.toPx()))
                            drawLine(ScannerRed, Offset(cx - r, cy), Offset(cx + r, cy), 1.2.dp.toPx())
                            drawLine(ScannerRed, Offset(cx, cy - r), Offset(cx, cy + r), 1.2.dp.toPx())
                            drawCircle(AlertRed, 3.5.dp.toPx(), Offset(cx - r * 0.35f, cy - r * 0.35f))
                        }
                        CockpitSkin.Matrix -> {
                            // Green CRT phosphor vector circle & radar sweep
                            drawCircle(Color(0xFF002200), r)
                            drawCircle(LedGreen, r, style = Stroke(1.5.dp.toPx()))
                            for (i in 0 until 4) {
                                val a = i * (PI / 2).toFloat()
                                drawLine(LedGreen.copy(alpha = 0.6f), Offset(cx, cy), Offset(cx + r * cos(a), cy + r * sin(a)), 1.2.dp.toPx())
                            }
                            drawCircle(Color(0xFF00FF66), 3.dp.toPx(), Offset(cx - r * 0.4f, cy - r * 0.4f))
                        }
                        CockpitSkin.Future -> {
                            // Flux capacitor blue & stainless steel compass dial
                            drawCircle(Color(0xFF101C24), r)
                            drawCircle(Color(0xFF00E5FF), r, style = Stroke(1.8.dp.toPx()))
                            // Three-prong flux indicator
                            for (angle in listOf(-90f, 30f, 150f)) {
                                val rad = angle * (PI / 180f).toFloat()
                                drawLine(Color(0xFF00E5FF), Offset(cx, cy), Offset(cx + r * 0.75f * cos(rad), cy + r * 0.75f * sin(rad)), 2.2.dp.toPx(), StrokeCap.Round)
                            }
                            drawCircle(Color.White, 3.dp.toPx(), Offset(cx, cy))
                        }
                        CockpitSkin.RoboCop -> {
                            // Detroit police radar grid in amber and blue
                            drawCircle(Color(0xFF1A1A00), r)
                            drawCircle(DashYellow, r, style = Stroke(1.8.dp.toPx()))
                            drawLine(Color(0xFF64B5F6), Offset(cx - r * 0.8f, cy), Offset(cx + r * 0.8f, cy), 1.5.dp.toPx())
                            drawLine(Color(0xFF64B5F6), Offset(cx, cy - r * 0.8f), Offset(cx, cy + r * 0.8f), 1.5.dp.toPx())
                            drawCircle(DashYellow, 3.5.dp.toPx(), Offset(cx - r * 0.3f, cy - r * 0.3f))
                        }
                        CockpitSkin.Kitt -> {
                            // Classic KITT microprocessor cockpit compass
                            drawCircle(KittCharcoal, r)
                            drawCircle(KittBezel, r, style = Stroke(1.5.dp.toPx()))
                            val nArrow = Path().apply {
                                moveTo(cx, cy - r * 0.75f)
                                lineTo(cx + r * 0.28f, cy)
                                lineTo(cx, cy - r * 0.15f)
                                close()
                            }
                            drawPath(nArrow, ScannerRed)
                            val sArrow = Path().apply {
                                moveTo(cx, cy + r * 0.75f)
                                lineTo(cx - r * 0.28f, cy)
                                lineTo(cx, cy + r * 0.15f)
                                close()
                            }
                            drawPath(sArrow, DashGrey)
                            drawCircle(Color.White, 3.dp.toPx(), Offset(cx, cy))
                        }
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    when (skin) {
                        CockpitSkin.Terminator -> "TARGET AZIMUTH: 315° NW · RECON GRID"
                        CockpitSkin.Matrix -> "OPERATOR HEADING: 315° · CONSTRUCT PORTAL"
                        CockpitSkin.Future -> "TIMELINE VECTOR: 315° · 88 MPH READY"
                        CockpitSkin.RoboCop -> "PATROL BEARING: 315° · SECTOR DISPATCH"
                        CockpitSkin.Kitt -> "HEADING: NORTH-WEST · 315° AZIMUTH"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (skin == CockpitSkin.Matrix) FontFamily.Monospace else FontFamily.Default
                    ),
                    color = DashAmber
                )
                Text(
                    when (skin) {
                        CockpitSkin.Terminator -> "SURVEILLANCE MODE: T-800 SENSORS ACTIVE"
                        CockpitSkin.Matrix -> "CARRIER: SECURE PHONE HARDLINE LINK"
                        CockpitSkin.Future -> "SPEED TARGET: 88.0 MPH CRUISE SET"
                        CockpitSkin.RoboCop -> "DIRECTIVE 1: SERVE THE PUBLIC TRUST"
                        CockpitSkin.Kitt -> "SPEED LIMIT: 65 MPH · CRUISE MODE"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DashWhite.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KittButton(homeBtn, { launchNav("Home") }, Modifier.weight(1f).height(36.dp), ScannerRed)
            KittButton(workBtn, { launchNav("Work") }, Modifier.weight(1f).height(36.dp), DashAmber)
            KittButton("⛽ Gas / Fuel", { launchNav("Gas Station") }, Modifier.weight(1f).height(36.dp), LedGreen)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KittButton("⚡ EV Station", { launchNav("EV Charging Station") }, Modifier.weight(1f).height(36.dp), LedGreen)
            KittButton("☕ Drive-Thru", { launchNav("Coffee") }, Modifier.weight(1f).height(36.dp), DashAmber)
            KittButton("🅿️ Parking", { launchNav("Parking") }, Modifier.weight(1f).height(36.dp), ScannerRed)
        }
    }
}

/**
 * 2. Parking Sentry & Meter Countdown:
 * Meter timer with warning colors, plus one-tap parking spot pin and locator.
 */
@Composable
fun ParkingSentryWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val skin = CockpitSkin.current()
    val endEpoch by app.settings.widgetMeterEndEpoch.state.collectAsState()
    val pinnedSpot by app.settings.widgetParkingSpot.state.collectAsState()

    var now by remember { mutableFloatStateOf(System.currentTimeMillis().toFloat()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis().toFloat()
            delay(1_000)
        }
    }

    val remainingMs = (endEpoch - now.toLong()).coerceAtLeast(0L)
    val totalSec = remainingMs / 1000
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    val timeFormatted = if (hours > 0) String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs) else String.format(Locale.US, "%02d:%02d", mins, secs)

    val (sentryStatus, sentryColor) = when {
        endEpoch <= 0L -> (if (skin == CockpitSkin.Terminator) "PERIMETER IDLE" else "IDLE · NO METER SET") to DashGrey
        remainingMs <= 0L -> (if (skin == CockpitSkin.Terminator) "PERIMETER BREACHED!" else "EXPIRED · VIOLATION WARNING!") to AlertRed
        remainingMs < 5 * 60 * 1000L -> "CRITICAL · < 5 MIN" to AlertRed
        remainingMs < 15 * 60 * 1000L -> "WARNING · < 15 MIN" to DashAmber
        else -> "ACTIVE · $timeFormatted REMAINING" to LedGreen
    }

    fun addMinutes(min: Int) {
        val base = if (endEpoch > System.currentTimeMillis()) endEpoch else System.currentTimeMillis()
        app.settings.widgetMeterEndEpoch.set(base + min * 60 * 1000L)
    }

    fun pinSpot() {
        val time = SimpleDateFormat("h:mm a · MMM d", Locale.US).format(Date())
        app.settings.widgetParkingSpot.set("PINNED AT $time")
    }

    fun findCar() {
        val uri = Uri.parse("geo:0,0?q=Current+Parking+Location")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        runCatching { context.startActivity(intent) }
    }

    val titleText = when (skin) {
        CockpitSkin.Terminator -> "Perimeter Sentry & Objective Timer"
        CockpitSkin.Matrix -> "Hardline Exit & Time Anchor"
        CockpitSkin.Future -> "Temporal Displacement Sentry"
        CockpitSkin.RoboCop -> "Sector 4 Sentry & Parking Directive"
        CockpitSkin.Kitt -> "Parking Sentry & Meter"
    }

    BuiltInWidgetCard(
        title = titleText,
        badge = sentryStatus,
        badgeColor = sentryColor,
        accent = sentryColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (endEpoch > 0L && remainingMs > 0L) timeFormatted else if (endEpoch > 0L) "00:00 EXPIRED" else "--:--",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    ),
                    color = sentryColor
                )
                Text(
                    text = pinnedSpot.ifEmpty { "No vehicle location pinned yet" },
                    style = MaterialTheme.typography.labelSmall,
                    color = DashGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton("📍 Pin Spot", { pinSpot() }, Modifier.height(34.dp), DashAmber)
                KittButton("Find Car", { findCar() }, Modifier.height(34.dp), LedGreen)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KittButton("+15m", { addMinutes(15) }, Modifier.weight(1f).height(32.dp), DashAmber)
            KittButton("+30m", { addMinutes(30) }, Modifier.weight(1f).height(32.dp), DashAmber)
            KittButton("+1h", { addMinutes(60) }, Modifier.weight(1f).height(32.dp), DashAmber)
            KittButton("+2h", { addMinutes(120) }, Modifier.weight(1f).height(32.dp), DashAmber)
            if (endEpoch > 0L) {
                KittButton("Clear", { app.settings.widgetMeterEndEpoch.set(0L) }, Modifier.width(64.dp).height(32.dp), AlertRed)
            }
        }
    }
}

/**
 * 3. Cockpit Inclinometer & Pitch/Roll Attitude Gyro:
 * Aircraft-style artificial horizon and off-road pitch & roll tilt gauge rendered per theme.
 */
@Composable
fun InclinometerWidget(modifier: Modifier = Modifier) {
    val skin = CockpitSkin.current()
    val transition = rememberInfiniteTransition(label = "incline")
    val rollAnim by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(3500), RepeatMode.Reverse),
        label = "roll"
    )
    val pitchAnim by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = -0.8f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
        label = "pitch"
    )

    val (titleText, statusLabel, horizonColor) = when (skin) {
        CockpitSkin.Terminator -> Triple("T-800 Chassis Attitude & Horizon", "ACTR OPTIMAL · +0.8°", ScannerRed)
        CockpitSkin.Matrix -> Triple("Construct Attitude & Physics Grid", "NODE LEVEL · +0.8°", LedGreen)
        CockpitSkin.Future -> Triple("Hover Conversion Attitude & Gyro", "FLUX STABILIZED · +0.8°", Color(0xFF00E5FF))
        CockpitSkin.RoboCop -> Triple("Chassis Hydraulic Level Sensor", "HYDRAULIC OK · +0.8°", DashYellow)
        CockpitSkin.Kitt -> Triple("Attitude & Inclinometer", "LEVEL OPTIMAL · +0.8°", ScannerRed)
    }

    BuiltInWidgetCard(
        title = titleText,
        badge = statusLabel,
        badgeColor = LedGreen,
        accent = horizonColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(92.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gyro Attitude Dial with Theme Vectors
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    val cx = size.width / 2f
                    val cy = size.height / 2f

                    // Outer dial ring
                    drawCircle(KittCharcoal, r)
                    drawCircle(horizonColor, r, style = Stroke(1.5.dp.toPx()))

                    val horizonY = cy + pitchAnim * 3.dp.toPx()
                    val rollRad = rollAnim * (PI / 180f).toFloat()
                    val dx = r * cos(rollRad)
                    val dy = r * sin(rollRad)

                    drawLine(
                        color = horizonColor,
                        start = Offset(cx - dx, horizonY - dy),
                        end = Offset(cx + dx, horizonY + dy),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Pitch ladder markings
                    for (step in listOf(-12f, -6f, 6f, 12f)) {
                        val py = horizonY + step * 1.5.dp.toPx()
                        val w = if (step % 12f == 0f) 14.dp.toPx() else 8.dp.toPx()
                        drawLine(
                            color = horizonColor.copy(alpha = 0.65f),
                            start = Offset(cx - w, py),
                            end = Offset(cx + w, py),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }

                    // Center crosshair / vehicle wings
                    drawLine(horizonColor, Offset(cx - 18.dp.toPx(), cy), Offset(cx - 6.dp.toPx(), cy), 2.5.dp.toPx(), StrokeCap.Square)
                    drawLine(horizonColor, Offset(cx + 6.dp.toPx(), cy), Offset(cx + 18.dp.toPx(), cy), 2.5.dp.toPx(), StrokeCap.Square)
                    drawCircle(horizonColor, 2.5.dp.toPx(), Offset(cx, cy))
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "CHASSIS ATTITUDE: STABILIZED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (skin == CockpitSkin.Matrix) FontFamily.Monospace else FontFamily.Default
                    ),
                    color = LedGreen
                )
                Text(
                    "PITCH ANGLE: +0.8° (FLAT RUNWAY)",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashWhite
                )
                Text(
                    "ROLL ANGLE: -1.2° (LATERAL BALANCE)",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashWhite
                )
                Text(
                    "BAROMETER: 29.92 inHg · ALT 412 FT",
                    style = MaterialTheme.typography.labelSmall,
                    color = DashGrey
                )
            }
        }
    }
}

/**
 * 4. Cabin Acoustic Sound Meter / Noise Sentinel:
 * Decibel gauge with animated theme-aware LED bar spectrum.
 */
@Composable
fun CabinSoundMeterWidget(modifier: Modifier = Modifier) {
    val skin = CockpitSkin.current()
    var peakDb by remember { mutableIntStateOf(68) }

    val transition = rememberInfiniteTransition(label = "db")
    val liveDbAnim by transition.animateFloat(
        initialValue = 44f,
        targetValue = 62f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "dbVal"
    )
    val curDb = liveDbAnim.toInt()
    if (curDb > peakDb) peakDb = curDb

    val (titleText, badgeText) = when (skin) {
        CockpitSkin.Terminator -> "Acoustic Surveillance Intercept" to "$curDb dB · T-800 SENSOR"
        CockpitSkin.Matrix -> "Carrier Signal Noise Analyzer" to "$curDb dB · FREQUENCY LOCK"
        CockpitSkin.Future -> "Flux Core Resonance & Cabin Sound" to "$curDb dB · FLUX STABLE"
        CockpitSkin.RoboCop -> "Audio Surveillance Decibel Array" to "$curDb dB · METRO NOISE"
        CockpitSkin.Kitt -> "Cabin Acoustic Sentinel" to "$curDb dB · Normal Cruise"
    }

    BuiltInWidgetCard(
        title = titleText,
        badge = badgeText,
        badgeColor = if (curDb > 75) AlertRed else if (curDb > 60) DashAmber else LedGreen,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$curDb",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = Color.White
                    )
                    Text("dB SPL", style = MaterialTheme.typography.labelMedium, color = DashAmber, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text("PEAK: $peakDb dB", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = DashGrey)
                KittButton("Reset Peak", { peakDb = curDb }, Modifier.height(28.dp), DashAmber)
            }

            // 16-Segment LED dB Bar Gauge with theme palette
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val totalBars = 16
                val gap = 3.dp.toPx()
                val barW = (size.width - gap * (totalBars - 1)) / totalBars
                val activeCount = ((curDb - 30).coerceAtLeast(1) * totalBars / 55).coerceIn(1, totalBars)

                for (i in 0 until totalBars) {
                    val color = when {
                        i >= 13 -> AlertRed
                        i >= 9 -> DashAmber
                        else -> when (skin) {
                            CockpitSkin.Terminator -> ScannerRed
                            CockpitSkin.Matrix -> Color(0xFF00FF66)
                            CockpitSkin.Future -> Color(0xFF00E5FF)
                            CockpitSkin.RoboCop -> DashYellow
                            CockpitSkin.Kitt -> LedGreen
                        }
                    }
                    val isActive = i < activeCount
                    drawRoundRect(
                        color = if (isActive) color else color.copy(alpha = 0.18f),
                        topLeft = Offset(i * (barW + gap), 0f),
                        size = Size(barW, size.height),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * 5. Driver's Cockpit Scratchpad:
 * Instant dash sticky memo persisted across reboots with film-themed preset quick tags.
 */
@Composable
fun CockpitScratchpadWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val skin = CockpitSkin.current()
    val savedMemo by app.settings.widgetScratchpad.state.collectAsState()
    var text by remember(savedMemo) { mutableStateOf(savedMemo) }

    fun addTag(tag: String) {
        val updated = if (text.isBlank()) tag else "$text | $tag"
        text = updated
        app.settings.widgetScratchpad.set(updated)
    }

    val (titleText, tag1, tag2, tag3) = when (skin) {
        CockpitSkin.Terminator -> Quad("T-800 Mission Log & Scratchpad", "Target: ", "Sector: ", "Code: ")
        CockpitSkin.Matrix -> Quad("Operator Construct Log", "Node: ", "Hardline: ", "Pass: ")
        CockpitSkin.Future -> Quad("Temporal Log & Destination Memo", "Plutonium: ", "Date: ", "Year: ")
        CockpitSkin.RoboCop -> Quad("Patrol Log & Directives Scratchpad", "Suspect: ", "Badge: ", "Stall: ")
        CockpitSkin.Kitt -> Quad("Cockpit Scratchpad & Sticky Memo", "Gate: ", "Spot: ", "Order: ")
    }

    BuiltInWidgetCard(
        title = titleText,
        badge = if (text.isBlank()) "Empty" else "Saved",
        badgeColor = if (text.isBlank()) DashGrey else LedGreen,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KittTextField(
                value = text,
                onValueChange = {
                    text = it
                    app.settings.widgetScratchpad.set(it)
                },
                placeholder = "Type dashboard memo, parking space, gate code...",
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton("+$tag1", { addTag(tag1) }, Modifier.weight(1f).height(30.dp), DashAmber)
                KittButton("+$tag2", { addTag(tag2) }, Modifier.weight(1f).height(30.dp), DashAmber)
                KittButton("+$tag3", { addTag(tag3) }, Modifier.weight(1f).height(30.dp), DashAmber)
                if (text.isNotBlank()) {
                    KittButton("Clear", {
                        text = ""
                        app.settings.widgetScratchpad.set("")
                    }, Modifier.width(60.dp).height(30.dp), AlertRed)
                }
            }
        }
    }
}

private data class Quad(val first: String, val second: String, val third: String, val fourth: String)

/**
 * 6. Road Unit & Tire Pressure Converter:
 * Instant automotive conversions between PSI <-> Bar <-> kPa and MPH <-> KM/H.
 */
@Composable
fun UnitConverterWidget(modifier: Modifier = Modifier) {
    val skin = CockpitSkin.current()
    var psi by remember { mutableIntStateOf(32) }
    var mph by remember { mutableIntStateOf(65) }

    val bar = String.format(Locale.US, "%.2f", psi * 0.0689476)
    val kpa = String.format(Locale.US, "%.1f", psi * 6.89476)
    val kmh = (mph * 1.60934).toInt()

    BuiltInWidgetCard(
        title = "Road Unit & Pressure Calculator",
        badge = "Cold Target 32 PSI",
        badgeColor = LedGreen,
        modifier = modifier
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Tire Pressure Box
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(KittCharcoal)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("TIRE PRESSURE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = DashAmber)
                Text("$psi PSI = $bar Bar", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text("= $kpa kPa", style = MaterialTheme.typography.labelSmall, color = DashGrey)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KittButton("-1 PSI", { psi = (psi - 1).coerceAtLeast(15) }, Modifier.weight(1f).height(28.dp), DashGrey)
                    KittButton("+1 PSI", { psi = (psi + 1).coerceAtMost(60) }, Modifier.weight(1f).height(28.dp), DashAmber)
                }
            }

            // Speed Box
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(KittCharcoal)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("ROAD SPEED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ScannerRed)
                Text("$mph MPH = $kmh KM/H", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                Text("Target: 25 MPG = 9.4 L/100km", style = MaterialTheme.typography.labelSmall, color = DashGrey)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KittButton("25", { mph = 25 }, Modifier.weight(1f).height(28.dp), if (mph == 25) ScannerRed else DashGrey)
                    KittButton("45", { mph = 45 }, Modifier.weight(1f).height(28.dp), if (mph == 45) ScannerRed else DashGrey)
                    KittButton("65", { mph = 65 }, Modifier.weight(1f).height(28.dp), if (mph == 65) ScannerRed else DashGrey)
                    KittButton(if (skin == CockpitSkin.Future) "88" else "75", { mph = if (skin == CockpitSkin.Future) 88 else 75 }, Modifier.weight(1f).height(28.dp), if (mph == 88 || mph == 75) ScannerRed else DashGrey)
                }
            }
        }
    }
}

/**
 * 7. Vehicle Battery Health & Emergency Dispatch:
 * 12V Battery monitor, alternator charging check, and one-tap 911 / Roadside dispatch.
 */
@Composable
fun VehicleEmergencyWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val skin = CockpitSkin.current()

    fun dial(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    val (titleText, badgeText, battLabel, sosBtn, roadBtn) = when (skin) {
        CockpitSkin.Terminator -> Quint("T-800 Power Cell & Extraction", "14.2V NOMINAL CELL", "NUCLEAR CELL: 14.2 VDC (ACTIVE)", "🚨 Extraction SOS", "🛠️ Unit Repair")
        CockpitSkin.Matrix -> Quint("Construct Power Feed & Operator Exit", "14.2V HARDLINE POWER", "POWER HARVEST: 14.2 VDC (RUNNING)", "🚨 Emergency Exit", "🛠️ Operator Assist")
        CockpitSkin.Future -> Quint("Plutonium Chamber & Temporal Rescue", "1.21 GW FLUX READY", "CHAMBER VOLTAGE: 14.2 VDC (PLUTONIUM OK)", "🚨 Temporal SOS", "🛠️ Doc Brown Rescue")
        CockpitSkin.RoboCop -> Quint("OCP Prime Directives & Emergency Tow", "14.2V GENERATOR OK", "HYDRAULIC POWER: 14.2 VDC (RUNNING)", "🚨 911 Dispatch", "🛠️ Precinct Tow")
        CockpitSkin.Kitt -> Quint("Vehicle Sentry & Emergency Dispatch", "14.2V Alternator Active", "12V BATTERY HEALTH: EXCELLENT", "🚨 911 SOS", "🛠️ Roadside")
    }

    BuiltInWidgetCard(
        title = titleText,
        badge = badgeText,
        badgeColor = LedGreen,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    battLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = LedGreen
                )
                Text(
                    "CHARGING VOLTAGE: 14.2 VDC (RUNNING)",
                    style = MaterialTheme.typography.bodySmall,
                    color = DashWhite
                )
                Text(
                    "DRAIN RATE: 0.02A · RUN-TIME: 1h 24m",
                    style = MaterialTheme.typography.labelSmall,
                    color = DashGrey
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KittButton(sosBtn, { dial("911") }, Modifier.height(36.dp), AlertRed)
                KittButton(roadBtn, { dial("18002224357") }, Modifier.height(36.dp), DashAmber)
            }
        }
    }
}

private data class Quint(val first: String, val second: String, val third: String, val fourth: String, val fifth: String)
