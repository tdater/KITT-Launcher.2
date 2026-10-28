package com.aiconsole.kittlauncher2.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aiconsole.kittlauncher2.obd.PidCatalog
import com.aiconsole.kittlauncher2.obd.PidReading
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashYellow
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

/** Holo-edition cockpit widgets: wireframe car with tire pressures, G-force circle, LED arcs, 16-band EQ. */

/**
 * Two-axis friction circle from the device's linear-acceleration sensor, with a trail and a
 * peak-hold dot. Axes follow the screen, so it reads right when the unit is mounted upright.
 * Shows NO SENSOR on units without an accelerometer.
 */
@Composable
fun GForceCircle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var peakX by remember { mutableFloatStateOf(0f) }
    var peakY by remember { mutableFloatStateOf(0f) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    DisposableEffect(sensor) {
        if (sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val y = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                // Light smoothing keeps the dot calm over bumps.
                gx += (x - gx) * 0.25f
                gy += (y - gy) * 0.25f
                val mag = hypot(gx, gy)
                if (mag > peak) { peak = mag; peakX = gx; peakY = gy }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }
    // Peak decays so it shows the recent maximum, not the all-time one.
    LaunchedEffect(sensor) {
        while (sensor != null) {
            kotlinx.coroutines.delay(250)
            peak *= 0.97f
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val r = minOf(size.width, size.height) / 2f - 4.dp.toPx()
            val c = center
            val grid = Stroke(1.dp.toPx())
            for (f in listOf(0.33f, 0.66f, 1f)) drawCircle(KittBezel, r * f, c, style = grid)
            drawLine(KittBezel, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(KittBezel, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            if (sensor != null) {
                drawCircle(DashAmber.copy(alpha = 0.6f), 4.dp.toPx(), Offset(c.x + peakX * r, c.y - peakY * r), style = Stroke(1.5.dp.toPx()))
                drawCircle(ScannerRed, 7.dp.toPx(), Offset(c.x + gx * r, c.y - gy * r))
            }
        }
        val text = if (sensor == null) "NO SENSOR" else "%.2f G".format(hypot(gx, gy))
        Text(text, style = MaterialTheme.typography.labelMedium, color = DashGrey, textAlign = TextAlign.Center)
    }
}

/**
 * A curved LED bar: [segments] lamps along an arc centred on [centerDeg] (0 = right, 90 = down)
 * spanning [sweepDeg]. Lamps fill from the start of the arc; [color] null grades green to red.
 */
@Composable
fun CurvedLedArc(
    level: Float,
    modifier: Modifier = Modifier,
    centerDeg: Float,
    sweepDeg: Float = 110f,
    segments: Int = 24,
    color: Color? = null,
    reverse: Boolean = false
) {
    Canvas(modifier) {
        val lit = (level.coerceIn(0f, 1f) * segments).roundToInt()
        val thickness = minOf(size.width, size.height) * 0.07f
        val radius = minOf(size.width, size.height) / 2f - thickness
        val topLeft = Offset(center.x - radius, center.y - radius)
        val segSweep = sweepDeg / segments
        val start = centerDeg - sweepDeg / 2f
        for (i in 0 until segments) {
            val index = if (reverse) segments - 1 - i else i
            val fraction = (index + 1f) / segments
            val on = color ?: when {
                fraction <= 0.6f -> LedGreen
                fraction <= 0.85f -> DashYellow
                else -> AlertRed
            }
            drawArc(
                color = if (i < lit) on else on.copy(alpha = 0.12f),
                startAngle = start + i * segSweep + 0.6f,
                sweepAngle = segSweep - 1.2f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(thickness)
            )
        }
    }
}

/** Vintage warning lamps; a lit lamp glows in its colour, an unlit one stays dim. */
data class Annunciator(val label: String, val lit: Boolean, val color: Color = DashAmber)

@Composable
fun AnnunciatorRow(tiles: List<Annunciator>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.forEach { tile ->
            val shape = MaterialTheme.shapes.extraSmall
            Box(
                Modifier
                    .weight(1f)
                    .border(1.dp, if (tile.lit) tile.color else KittBezel, shape)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tile.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tile.lit) tile.color else tile.color.copy(alpha = 0.25f),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 16-band LED spectrum with peak-hold lamps. Real data comes from the system audio mix via
 * [Visualizer], which needs the microphone permission; without it, or on units whose audio
 * stack refuses the visualizer, the bars stay at rest and the label says why.
 */
@Composable
fun LedEqualizer16(modifier: Modifier = Modifier, active: Boolean = true, micGranted: Boolean = true, labels: Boolean = false) {
    val context = LocalContext.current
    val bands = 16
    val levels = remember { FloatArray(bands) }
    val peaks = remember { FloatArray(bands) }
    var tick by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("") }
    val allowed = micGranted
    DisposableEffect(active, allowed) {
        if (!active) return@DisposableEffect onDispose { }
        if (!allowed) {
            status = "EQ NEEDS MIC PERMISSION"
            return@DisposableEffect onDispose { }
        }
        val visualizer = try {
            Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(256)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft ?: return
                        val usable = fft.size / 2 - 1
                        for (b in 0 until bands) {
                            // Log-spaced bins so bass gets more than one lamp column.
                            val from = 1 + ((usable - 1) * (b.toFloat() / bands).let { it * it }).toInt()
                            val to = (1 + ((usable - 1) * ((b + 1f) / bands).let { it * it }).toInt()).coerceAtLeast(from + 1)
                            var sum = 0f
                            for (k in from until minOf(to, usable)) {
                                val re = fft[2 * k].toInt()
                                val im = fft[2 * k + 1].toInt()
                                sum += hypot(re.toFloat(), im.toFloat())
                            }
                            val mag = sum / (to - from).coerceAtLeast(1)
                            val target = ((20f * log10(mag + 1f)) / 42f).coerceIn(0f, 1f)
                            levels[b] = if (target > levels[b]) target else levels[b] * 0.85f
                            if (levels[b] >= peaks[b]) peaks[b] = levels[b] else peaks[b] = (peaks[b] - 0.02f).coerceAtLeast(0f)
                        }
                        tick++
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            status = "EQ UNAVAILABLE ON THIS UNIT"
            null
        }
        onDispose {
            try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) { }
        }
    }
    Column(modifier) {
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            tick.let { }
            val gap = 4.dp.toPx()
            val colW = (size.width - gap * (bands - 1)) / bands
            val rows = 12
            val rowGap = 2.dp.toPx()
            val rowH = (size.height - rowGap * (rows - 1)) / rows
            for (b in 0 until bands) {
                val lit = (levels[b] * rows).roundToInt()
                val peakRow = (peaks[b] * rows).roundToInt().coerceIn(0, rows)
                for (r in 0 until rows) {
                    val fraction = (r + 1f) / rows
                    val on = when {
                        fraction <= 0.6f -> LedGreen
                        fraction <= 0.85f -> DashAmber
                        else -> ScannerRed
                    }
                    val shown = r < lit || (r + 1 == peakRow && peakRow > 0)
                    drawRect(
                        if (shown) on else on.copy(alpha = 0.10f),
                        Offset(b * (colW + gap), size.height - (r + 1) * rowH - r * rowGap),
                        Size(colW, rowH)
                    )
                }
            }
        }
        if (labels) {
            val names = listOf("40", "60", "80", "120", "160", "250", "400", "600", "1k", "1.5k", "2.5k", "4k", "6k", "8k", "12k", "16k")
            Row(Modifier.fillMaxWidth()) {
                names.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey, textAlign = TextAlign.Center, maxLines = 1) }
            }
        }
        if (status.isNotEmpty()) {
            Text(status, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = DashGrey, textAlign = TextAlign.Center)
        }
    }
}
