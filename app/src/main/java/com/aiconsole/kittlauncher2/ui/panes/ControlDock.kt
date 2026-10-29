package com.aiconsole.kittlauncher2.ui.panes

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.aiconsole.kittlauncher2.ui.components.skinFrame
import com.aiconsole.kittlauncher2.BuildConfig
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.ui.components.FilledKey
import com.aiconsole.kittlauncher2.ui.components.KeyGlyph
import com.aiconsole.kittlauncher2.ui.components.VoiceModulator
import com.aiconsole.kittlauncher2.ui.gauges.SegmentDisplay
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.KittCharcoal
import com.aiconsole.kittlauncher2.ui.theme.KittGrid
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import com.aiconsole.kittlauncher2.ui.theme.assistantName
import com.aiconsole.kittlauncher2.ui.trip.rememberRecentTrips
import java.util.Locale

/** Bottom pane: stationary dock with climate, media and vehicle shortcuts. */
@Composable
fun ControlDock(
    onAssistant: () -> Unit,
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier,
    kittSpeaking: Boolean = false,
    onTrip: () -> Unit = {},
    metric: Boolean = false
) {
    if (BuildConfig.HOLO_DESIGN) {
        ControlDockHolo(onAssistant, onDiagnostics, onConfig, modifier, kittSpeaking, onTrip, metric)
        return
    }
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(KittCharcoal)
            .border(1.dp, KittBezel, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TripSection(onTrip, metric, Modifier.weight(1.3f).fillMaxHeight())
            MediaSection(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onMediaKey = { sendMediaKey(context, it) }
            )
            VehicleSection(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onSystemSettings = { openSettings(context, Settings.ACTION_SETTINGS) },
                onTrips = onTrip,
                onDiagnostics = onDiagnostics,
                onConfig = onConfig
            )
            AssistantSection(onAssistant, kittSpeaking, Modifier.weight(0.5f).fillMaxHeight())
        }
    }
}

/**
 * Holo-edition portrait bottom dock, laid out like the design: the trip computer over now-playing over the
 * vehicle shortcuts on the left, the voice modulator over the big KITT button on the right.
 * SELF-TEST opens KITT Config, whose first section is the system check.
 */
@Composable
private fun ControlDockHolo(
    onAssistant: () -> Unit,
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier,
    kittSpeaking: Boolean = false,
    onTrip: () -> Unit = {},
    metric: Boolean = false
) {
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .clip(shape)
            .background(KittCharcoal)
            .skinFrame(shape, ScannerRed)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TripComputer(onTrip, metric, Modifier.weight(0.8f).fillMaxWidth())
            NowPlayingCard({ sendMediaKey(context, it) }, Modifier.weight(1.2f).fillMaxWidth())
            Row(Modifier.weight(1.05f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledKey(KeyGlyph.Diag, "Diag", onDiagnostics, Modifier.weight(1f).fillMaxHeight(), LedGreen)
                FilledKey(KeyGlyph.Check, "Test", onConfig, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                FilledKey(KeyGlyph.Route, "Trips", onTrip, Modifier.weight(1f).fillMaxHeight(), DashAmber)
                FilledKey(KeyGlyph.Gear, "System", { openSettings(context, Settings.ACTION_SETTINGS) }, Modifier.weight(1f).fillMaxHeight(), Color(0xFF42A5F5))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val boxShape = MaterialTheme.shapes.small
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(boxShape).background(KittBlack).skinFrame(boxShape, ScannerRed).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("VOICE MODULATOR", style = MaterialTheme.typography.labelSmall, color = DashWhite, maxLines = 1)
                VoiceModulator(active = kittSpeaking, modifier = Modifier.fillMaxWidth().weight(1f))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .clip(boxShape)
                    .background(KittBlack)
                    .skinFrame(boxShape, ScannerRed, selected = true)
                    .clickable(onClick = onAssistant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    assistantName().uppercase(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.ExtraBold,
                        shadow = Shadow(ScannerRed, Offset.Zero, 18f)
                    ),
                    color = ScannerRed,
                    maxLines = 1
                )
            }
        }
    }
}

/** The trip computer: distance, time and average speed in segment digits, tap for the trip screen. */
@Composable
private fun TripComputer(onClick: () -> Unit, metric: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val latest = rememberRecentTrips(app.tripDatabase.tripMetricDao(), days = 1)?.firstOrNull()
    val shape = MaterialTheme.shapes.small
    Row(
        modifier.clip(shape).background(KittBlack).skinFrame(shape, DashAmber).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val hours = latest?.let { it.durationMs / 3_600_000.0 }?.takeIf { it > 0.0 }
        val avg = latest?.let { t -> hours?.let { h -> (if (metric) t.distanceKm else t.distanceKm * 0.621371) / h } }
        TripColumn("TRIP DIST", latest?.let { "%.1f".format(Locale.US, if (metric) it.distanceKm else it.distanceKm * 0.621371) } ?: "--", if (metric) "KM" else "MI", Modifier.weight(1f))
        TripColumn("TRIP TIME", latest?.let { (it.durationMs / 60_000).toString() } ?: "--", "MIN", Modifier.weight(1f))
        TripColumn("AVG SPEED", avg?.let { "%.0f".format(Locale.US, it) } ?: "--", if (metric) "KM/H" else "MPH", Modifier.weight(1f))
    }
}

@Composable
private fun TripColumn(label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = DashWhite, maxLines = 1)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            SegmentDisplay(value, Modifier.weight(1f).fillMaxHeight(), DashAmber, cells = 4)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
        }
    }
}

/** Album art, the track and PLAY / PAUSE / FWD / REV, falling back to plain media keys with no session. */
@Composable
private fun NowPlayingCard(onMediaKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val now by app.media.nowPlaying.collectAsState()
    val current = now
    val shape = MaterialTheme.shapes.small
    Row(
        modifier.clip(shape).background(KittBlack).skinFrame(shape, ScannerRed).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f).clip(shape).background(KittCharcoal).border(1.dp, KittGrid, shape),
            contentAlignment = Alignment.Center
        ) {
            val art = current?.art
            if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("♪", style = MaterialTheme.typography.titleLarge, color = DashGrey)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                current?.title?.uppercase() ?: "NOW-PLAYING",
                style = MaterialTheme.typography.labelMedium,
                color = DashAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledKey(KeyGlyph.Play, "Play", { if (current != null) { if (!current.playing) app.media.playPause() } else onMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed, active = current?.playing == true)
                FilledKey(KeyGlyph.Pause, "Pause", { if (current != null) { if (current.playing) app.media.playPause() } else onMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                FilledKey(KeyGlyph.Next, "Fwd", { if (current != null) app.media.next() else onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                FilledKey(KeyGlyph.Prev, "Rev", { if (current != null) app.media.previous() else onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
            }
        }
    }
}

/**
 * Slim one-row dock for short landscape screens (7"–10" head units), where the stacked dock
 * would eat a quarter of the height: last trip, now playing with media keys, and the vehicle
 * buttons. KITT itself sits at the top of the side rail.
 */
@Composable
fun DockBar(
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier,
    onTrip: () -> Unit = {},
    metric: Boolean = false
) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val shape = MaterialTheme.shapes.medium
    val latest = rememberRecentTrips(app.tripDatabase.tripMetricDao(), days = 1)?.firstOrNull()
    val now by app.media.nowPlaying.collectAsState()
    val current = now
    Row(
        modifier
            .clip(shape)
            .background(KittCharcoal)
            .border(1.dp, KittBezel, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Last trip: tap for the trip computer.
        Row(
            Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
                .border(1.dp, KittGrid, MaterialTheme.shapes.small)
                .clickable(onClick = onTrip)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentDisplay(
                latest?.let { "%.1f".format(Locale.US, if (metric) it.distanceKm else it.distanceKm * 0.621371) } ?: "--",
                Modifier.weight(1f).fillMaxHeight(0.8f),
                DashAmber,
                cells = 4
            )
            Text(if (metric) "KM" else "MI", style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
            SegmentDisplay(
                latest?.let { t -> (if (metric) t.litersPer100Km else t.mpg)?.let { "%.1f".format(Locale.US, it) } } ?: "--",
                Modifier.weight(1f).fillMaxHeight(0.8f),
                LedGreen,
                cells = 4
            )
            Text(if (metric) "L/100" else "MPG", style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
        }
        // Now playing with media keys.
        Row(
            Modifier
                .weight(2f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
                .border(1.dp, KittGrid, MaterialTheme.shapes.small)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    current?.let { it.title ?: it.appLabel } ?: "NO MEDIA",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (current != null) DashWhite else DashGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                current?.let {
                    Text(
                        it.artist ?: it.appLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = DashGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledKey(KeyGlyph.Prev, "Prev", { if (current != null) app.media.previous() else sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, Modifier.width(68.dp).fillMaxHeight(), ScannerRed)
            FilledKey(
                if (current?.playing == true) KeyGlyph.Pause else KeyGlyph.Play,
                if (current?.playing == true) "Pause" else "Play",
                { if (current != null) app.media.playPause() else sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                Modifier.width(72.dp).fillMaxHeight(), ScannerRed, active = current?.playing == true
            )
            FilledKey(KeyGlyph.Next, "Next", { if (current != null) app.media.next() else sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) }, Modifier.width(68.dp).fillMaxHeight(), ScannerRed)
        }
        FilledKey(KeyGlyph.Diag, "Diag", onDiagnostics, Modifier.weight(0.7f).fillMaxHeight(), LedGreen)
        FilledKey(KeyGlyph.Route, "Trips", onTrip, Modifier.weight(0.7f).fillMaxHeight(), DashAmber)
        FilledKey(KeyGlyph.Sliders, "Config", onConfig, Modifier.weight(0.7f).fillMaxHeight(), ScannerRed)
        FilledKey(KeyGlyph.Gear, "System", { openSettings(context, Settings.ACTION_SETTINGS) }, Modifier.weight(0.7f).fillMaxHeight(), Color(0xFF42A5F5))
    }
}

/** Whole section is one large touch target that opens the KITT assistant. */
@Composable
private fun AssistantSection(onClick: () -> Unit, speaking: Boolean, modifier: Modifier = Modifier) {
    DockSection("Assistant", ScannerRed, modifier.clickable(onClick = onClick)) {
        VoiceModulator(
            active = speaking,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Text(
            text = assistantName(),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            color = ScannerRed,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DockSection(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittGrid, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
        content()
    }
}

/**
 * Last drive at a glance; tap for the trip computer. (This slot used to hold climate sliders,
 * but third-party apps can't reach the HVAC bus on aftermarket head units.)
 */
@Composable
private fun TripSection(onClick: () -> Unit, metric: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val latest = rememberRecentTrips(app.tripDatabase.tripMetricDao(), days = 1)?.firstOrNull()
    DockSection("Trip", DashAmber, modifier.clickable(onClick = onClick)) {
        TripLine(
            "DIST",
            latest?.let { "%.1f".format(Locale.US, if (metric) it.distanceKm else it.distanceKm * 0.621371) } ?: "--",
            if (metric) "KM" else "MI",
            DashAmber,
            Modifier.weight(1f)
        )
        TripLine(
            "ECON",
            latest?.let { t -> (if (metric) t.litersPer100Km else t.mpg)?.let { "%.1f".format(Locale.US, it) } } ?: "--",
            if (metric) "L/100" else "MPG",
            LedGreen,
            Modifier.weight(1f)
        )
        TripLine(
            "TIME",
            latest?.let { (it.durationMs / 60_000).toString() } ?: "--",
            "MIN",
            DashAmber,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun TripLine(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = DashGrey)
        SegmentDisplay(value, Modifier.weight(1f).fillMaxHeight(0.8f), color, cells = 5)
        Text(
            text = unit,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelSmall,
            color = DashGrey,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

/** Compact now-playing: track and progress when a media session is visible, else plain media keys. */
@Composable
private fun MediaSection(onMediaKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val now by app.media.nowPlaying.collectAsState()
    val current = now

    DockSection("Media", ScannerRed, modifier) {
        if (current != null) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    current.title ?: current.appLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = DashWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    current.artist ?: current.appLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = DashGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProgressBar(current)
            }
        } else {
            VoiceModulator(
                active = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledKey(KeyGlyph.Prev, "Prev", { if (current != null) app.media.previous() else onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
            FilledKey(
                if (current?.playing == true) KeyGlyph.Pause else KeyGlyph.Play,
                if (current?.playing == true) "Pause" else "Play",
                { if (current != null) app.media.playPause() else onMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                Modifier.weight(1f).fillMaxHeight(), ScannerRed, active = current?.playing == true
            )
            FilledKey(KeyGlyph.Next, "Next", { if (current != null) app.media.next() else onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
        }
    }
}

@Composable
private fun VehicleSection(
    onSystemSettings: () -> Unit,
    onTrips: () -> Unit,
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    DockSection("Vehicle", LedGreen, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledKey(KeyGlyph.Diag, "Diag", onDiagnostics, Modifier.weight(1f).fillMaxHeight(), LedGreen)
            FilledKey(KeyGlyph.Route, "Trips", onTrips, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilledKey(KeyGlyph.Sliders, "Config", onConfig, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
            FilledKey(KeyGlyph.Gear, "System", onSystemSettings, Modifier.weight(1f).fillMaxHeight(), Color(0xFF42A5F5))
        }
    }
}

/** Sends a media key to whichever app currently holds the active media session. */
private fun sendMediaKey(context: Context, keyCode: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some head-unit ROMs strip individual settings screens.
    }
}
