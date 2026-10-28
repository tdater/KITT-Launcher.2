package com.aiconsole.kittlauncher2.ui.panes

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.media.audiofx.Equalizer
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.media.NowPlaying
import com.aiconsole.kittlauncher2.nav.NavBridge
import com.aiconsole.kittlauncher2.ui.components.CockpitSkin
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.LedBarGauge
import com.aiconsole.kittlauncher2.ui.components.LedEqualizer16
import com.aiconsole.kittlauncher2.ui.components.VoiceModulator
import com.aiconsole.kittlauncher2.ui.components.skinFrame
import com.aiconsole.kittlauncher2.ui.gauges.SegmentDisplay
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The media page as a hi-fi deck, always on screen even when nothing plays: the progress bar with
 * the title and track across the top, then album art with a record sliding out of it, the 16-band
 * LED spectrum with frequency labels, the track clock in 7-segment digits (three stacked time
 * circuits on Back to the Future), a CRT queue terminal, a system EQ, and the transport with LED
 * lamps and rocker switches. The spectrum needs microphone permission (it taps the audio mix) and
 * the system EQ needs a device that exposes a global equalizer; each says so when unavailable.
 * On a short landscape page everything shares one row, so no part is pushed off the screen.
 */
@Composable
fun MediaDeck(app: KittApplication, now: NowPlaying?, hasAccess: Boolean, modifier: Modifier = Modifier, onLibrary: () -> Unit = {}) {
    val skin = CockpitSkin.current()
    val context = LocalContext.current
    var mic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(now?.playing) {
        while (now?.playing == true) {
            clock = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val position = now?.positionNow(clock) ?: 0L
    val duration = now?.durationMs ?: 0L

    BoxWithConstraints(modifier) {
        val wide = maxWidth > maxHeight
        val art = @Composable { m: Modifier ->
            // The album sleeve with a record sliding out of its right edge, like the design.
            Box(m.aspectRatio(1.2f)) {
                Canvas(Modifier.fillMaxSize()) {
                    val d = size.height * 0.94f
                    val c = Offset(size.width - d / 2f, size.height / 2f)
                    drawCircle(Color(0xFF0C0C0C), d / 2f, c)
                    drawCircle(KittBezel, d / 2f, c, style = Stroke(1.dp.toPx()))
                    for (k in listOf(0.82f, 0.66f, 0.5f)) drawCircle(Color.White.copy(alpha = 0.06f), d / 2f * k, c, style = Stroke(1.dp.toPx()))
                    drawCircle(ScannerRed.copy(alpha = 0.85f), d / 2f * 0.26f, c)
                    drawCircle(Color.Black, d / 2f * 0.05f, c)
                }
                Box(
                    Modifier.fillMaxHeight().aspectRatio(1f).clip(MaterialTheme.shapes.small).background(KittBlack).border(1.dp, KittBezel, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = now?.art
                    if (bitmap != null) Image(remember(bitmap) { bitmap.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else VoiceModulator(active = now?.playing == true, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val spectrum = @Composable { m: Modifier ->
            Column(m) {
                LedEqualizer16(Modifier.weight(1f).fillMaxWidth(), active = true, micGranted = mic, labels = true)
                if (!mic) KittButton("Enable spectrum (microphone)", { askMic.launch(Manifest.permission.RECORD_AUDIO) }, Modifier.fillMaxWidth().height(30.dp), DashAmber)
            }
        }
        val accessButton = @Composable {
            KittButton(
                "Allow notification access",
                {
                    try {
                        context.startActivity(NavBridge.accessSettingsIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                    }
                },
                Modifier.fillMaxWidth().height(34.dp), ScannerRed, active = true
            )
        }

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The red progress bar across the top, with the title and the playing track written over it.
            Box(Modifier.fillMaxWidth().height(if (wide) 26.dp else 24.dp)) {
                LedBarGauge(
                    if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    Modifier.fillMaxSize(), segments = 60, color = ScannerRed
                )
                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "MEDIA PLAYER",
                        style = MaterialTheme.typography.titleSmall.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, shadow = Shadow(Color.Black, Offset.Zero, 8f)),
                        color = Color.White, maxLines = 1
                    )
                    Text(
                        (now?.let { listOfNotNull(it.title, it.artist).joinToString(" - ") } ?: "NOTHING PLAYING").uppercase(),
                        Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.labelMedium.copy(shadow = Shadow(Color.Black, Offset.Zero, 8f)),
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    art(Modifier.fillMaxHeight())
                    Column(Modifier.weight(1.7f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        spectrum(Modifier.weight(1f).fillMaxWidth())
                        TrackClock(skin, position, duration, Modifier.fillMaxWidth().height(if (skin == CockpitSkin.Future) 66.dp else 40.dp))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!hasAccess) accessButton()
                        QueueTerminal(skin.queueTitle, now?.queue.orEmpty(), now?.title, Modifier.weight(1f).fillMaxWidth())
                        SystemEqPanel(Modifier.fillMaxWidth().height(58.dp))
                    }
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!hasAccess) accessButton()
                    art(Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally))
                    spectrum(Modifier.fillMaxWidth().height(150.dp))
                    TrackClock(skin, position, duration, Modifier.fillMaxWidth().height(if (skin == CockpitSkin.Future) 96.dp else 56.dp))
                    QueueTerminal(skin.queueTitle, now?.queue.orEmpty(), now?.title, Modifier.fillMaxWidth().height(130.dp))
                    SystemEqPanel(Modifier.fillMaxWidth().height(96.dp))
                }
            }
            // Rockers either side (open the playing app, open the library), five LED-labelled keys between.
            val playing = now?.playing == true
            val shuffleOk = now?.actions?.any { "shuffle" in it } == true
            val repeatOk = now?.actions?.any { "repeat" in it } == true
            Row(Modifier.fillMaxWidth().height(if (wide) 66.dp else 84.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Rocker("OPEN APP", now != null) { app.media.openApp() }
                TransportKey("PREV", "◀◀", false, now != null, Modifier.weight(1f)) { app.media.previous() }
                TransportKey("PLAY/PAUSE", if (playing) "❚❚" else "▶", playing, now != null, Modifier.weight(1.3f)) { app.media.playPause() }
                TransportKey("NEXT", "▶▶", false, now != null, Modifier.weight(1f)) { app.media.next() }
                TransportKey("REPEAT", "↻", repeatOk, repeatOk, Modifier.weight(1f)) { app.media.fireAction("repeat") }
                TransportKey("SHUFFLE", "⇄", shuffleOk, shuffleOk, Modifier.weight(1f)) { app.media.fireAction("shuffle") }
                Rocker("LIBRARY", true, onLibrary)
            }
        }
    }
}

private fun clockText(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(Locale.US, (s / 60).coerceAtMost(99), s % 60)
}

/** Track clock: red 7-segment elapsed / total, or Back to the Future's three stacked time circuits. */
@Composable
private fun TrackClock(skin: CockpitSkin, position: Long, duration: Long, modifier: Modifier) {
    if (skin == CockpitSkin.Future) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                Triple("DESTINATION TIME", clockText((duration - position).coerceAtLeast(0)), Color(0xFFFF3B30)),
                Triple("PRESENT TIME", clockText(position), LedGreen),
                Triple("LAST TIME DEPARTED", clockText(duration), DashAmber)
            ).forEach { (label, value, color) ->
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
                    SegmentDisplay(value, Modifier.weight(1f).fillMaxHeight(), color, cells = 5)
                }
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentDisplay(clockText(position), Modifier.weight(1f).fillMaxHeight(), ScannerRed, cells = 5)
            Text("/", style = MaterialTheme.typography.headlineSmall, color = DashGrey)
            SegmentDisplay(clockText(duration), Modifier.weight(1f).fillMaxHeight(), ScannerRed, cells = 5)
        }
    }
}

/** Amber CRT showing the playing app's own queue; apps that don't publish one say so. */
@Composable
private fun QueueTerminal(title: String, queue: List<String>, current: String?, modifier: Modifier) {
    val shape = MaterialTheme.shapes.small
    Column(modifier.clip(shape).background(KittBlack).border(1.dp, DashAmber.copy(alpha = 0.6f), shape).padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = DashAmber, fontFamily = FontFamily.Monospace, maxLines = 1)
        Box(Modifier.fillMaxWidth().height(1.dp).background(DashAmber.copy(alpha = 0.5f)))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
            if (queue.isEmpty()) {
                Text("NO QUEUE FROM THIS APP", style = MaterialTheme.typography.labelSmall, color = DashGrey, fontFamily = FontFamily.Monospace)
            } else {
                queue.forEachIndexed { i, name ->
                    Text(
                        "${i + 1}. ${name.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (name == current) ScannerRed else DashAmber,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The device's global equalizer, kept for the life of the process so the curve keeps applying. */
private object SystemEq {
    private var eq: Equalizer? = null
    private var tried = false

    fun get(): Equalizer? {
        if (!tried) {
            tried = true
            eq = try {
                Equalizer(0, 0).apply { enabled = true }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
        return eq
    }
}

/** One fader per band the device's equalizer offers (often five), saved and restored. */
@Composable
private fun SystemEqPanel(modifier: Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kitt_eq", android.content.Context.MODE_PRIVATE) }
    val eq = remember { SystemEq.get() }
    val shape = MaterialTheme.shapes.small
    Column(modifier.clip(shape).background(KittBlack).border(1.dp, KittBezel, shape).padding(6.dp)) {
        Text(
            if (eq == null) "EQ UNAVAILABLE ON THIS UNIT" else "${eq.numberOfBands}-BAND EQ",
            style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1
        )
        if (eq != null) {
            val min = eq.bandLevelRange[0].toInt()
            val max = eq.bandLevelRange[1].toInt()
            val saved = remember { prefs.getString("levels", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }.orEmpty() }
            val levels = remember {
                mutableStateOf(
                    List(eq.numberOfBands.toInt()) { b -> (saved.getOrNull(b) ?: ((eq.getBandLevel(b.toShort()) - min).toFloat() / (max - min))).coerceIn(0f, 1f) }
                )
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                levels.value.forEachIndexed { b, v ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Fader(v, Modifier.weight(1f).fillMaxWidth()) { nv ->
                            levels.value = levels.value.toMutableList().also { it[b] = nv }
                            try { eq.setBandLevel(b.toShort(), (min + nv * (max - min)).toInt().toShort()) } catch (_: Exception) { }
                            prefs.edit().putString("levels", levels.value.joinToString(",")).apply()
                        }
                    }
                }
            }
        }
    }
}

/** Vertical slider: a track and a red LED knob, drag or tap to set 0..1. */
@Composable
private fun Fader(value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { onChange(1f - (it.y / size.height).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> onChange(1f - (change.position.y / size.height).coerceIn(0f, 1f)) } }
    ) {
        val x = size.width / 2f
        drawLine(DashGrey.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
        val y = (1f - value) * size.height
        drawRect(Color(0xFF222222), Offset(x - 9.dp.toPx(), y - 5.dp.toPx()), Size(18.dp.toPx(), 10.dp.toPx()))
        drawLine(ScannerRed, Offset(x - 9.dp.toPx(), y), Offset(x + 9.dp.toPx(), y), 2.dp.toPx())
    }
}

/** A transport key with its LED and bracketed label above it; dim and inert when the playing app doesn't offer it. */
@Composable
private fun TransportKey(label: String, glyph: String, lamp: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Canvas(Modifier.size(6.dp)) { drawCircle(if (lamp) ScannerRed else ScannerRed.copy(alpha = 0.25f)) }
            Text("[$label]", style = MaterialTheme.typography.labelSmall, color = if (enabled) DashWhite else DashGrey, maxLines = 1, softWrap = false)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(shape)
                .background(if (lamp) ScannerRed.copy(alpha = 0.22f) else Color(0xFF151515))
                .skinFrame(shape, if (enabled) DashAmber else DashGrey.copy(alpha = 0.5f), selected = lamp)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, style = MaterialTheme.typography.titleLarge, color = if (enabled) DashAmber else DashGrey.copy(alpha = 0.5f), maxLines = 1)
        }
    }
}

/** A rocker switch: a dark two-position body with a red LED, labelled underneath. */
@Composable
private fun Rocker(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Column(Modifier.fillMaxHeight().width(54.dp).clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.weight(1f).fillMaxWidth(0.7f).clip(shape).background(Color(0xFF1B1B1B)).border(1.dp, KittBezel, shape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                drawLine(Color.White.copy(alpha = 0.12f), Offset(size.width * 0.2f, h * 0.5f), Offset(size.width * 0.8f, h * 0.5f), 2.dp.toPx())
                drawRect(if (enabled) ScannerRed else ScannerRed.copy(alpha = 0.2f), Offset(size.width * 0.35f, h * 0.2f), Size(size.width * 0.3f, 4.dp.toPx()))
                for (k in 0..3) drawLine(Color.White.copy(alpha = 0.10f), Offset(size.width * 0.2f, h * (0.62f + k * 0.07f)), Offset(size.width * 0.8f, h * (0.62f + k * 0.07f)), 1.dp.toPx())
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1, softWrap = false)
    }
}
