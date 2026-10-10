package com.aiconsole.kittlauncher2.ui.panes

import android.content.ActivityNotFoundException
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.media.NowPlaying
import com.aiconsole.kittlauncher2.nav.NavBridge
import com.aiconsole.kittlauncher2.ui.components.HelpText
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.components.VoiceModulator
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.delay
import java.util.Locale

/** Media page: artwork, track, progress and large transport buttons for the active session. */
@Composable
fun MediaPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val now by app.media.nowPlaying.collectAsState()
    val hasAccess by app.media.hasAccess.collectAsState()

    KittPanel(
        title = "Media",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            now?.let { Text(it.appLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = DashAmber) }
        }
    ) {
        val playing = now
        when {
            !hasAccess -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                HelpText(
                    "Allow notification access so KITT can show what's playing in Spotify, YouTube Music, radio and other apps. " +
                        "It's the same access the map uses for turn-by-turn."
                )
                KittButton(
                    label = "Allow notification access",
                    onClick = {
                        try {
                            context.startActivity(NavBridge.accessSettingsIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: ActivityNotFoundException) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    color = ScannerRed,
                    active = true
                )
            }
            playing == null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                VoiceModulator(active = false, modifier = Modifier.fillMaxWidth().height(80.dp))
                Text(
                    "NOTHING PLAYING",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    color = DashGrey,
                    textAlign = TextAlign.Center
                )
                HelpText("Start music in any app and it appears here.", Modifier.fillMaxWidth())
            }
            else -> NowPlayingBody(app, playing)
        }
    }
}

@Composable
private fun NowPlayingBody(app: KittApplication, now: NowPlaying) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val art = @Composable { artModifier: Modifier ->
            Box(
                artModifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(KittBlack)
                    .border(1.dp, KittBezel, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = now.art
                if (bitmap != null) {
                    Image(remember(bitmap) { bitmap.asImageBitmap() }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    VoiceModulator(active = now.playing, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val details = @Composable { detailModifier: Modifier ->
            Column(detailModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    now.title ?: "Unknown track",
                    style = MaterialTheme.typography.headlineSmall,
                    color = DashWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                now.artist?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = DashAmber, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ProgressBar(now)
                Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KittButton("Prev", { app.media.previous() }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                    KittButton(
                        if (now.playing) "Pause" else "Play",
                        { app.media.playPause() },
                        Modifier.weight(1.4f).fillMaxHeight(),
                        ScannerRed,
                        active = now.playing
                    )
                    KittButton("Next", { app.media.next() }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                }
                KittButton("Open ${now.appLabel}", { app.media.openApp() }, Modifier.fillMaxWidth().height(48.dp), DashAmber)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                art(Modifier.fillMaxHeight(0.9f))
                details(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                art(Modifier.weight(1f, fill = false).fillMaxWidth(0.7f))
                details(Modifier.fillMaxWidth())
            }
        }
    }
}

/** LED-segment progress bar with elapsed / total time. */
@Composable
fun ProgressBar(now: NowPlaying, modifier: Modifier = Modifier) {
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(now.playing) {
        while (now.playing) {
            clock = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val position = now.positionNow(clock)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val segments = 40
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (segments - 1)) / segments
            val lit = if (now.durationMs > 0) (segments * position / now.durationMs.toFloat()).toInt() else 0
            for (i in 0 until segments) {
                drawRect(
                    if (i < lit) ScannerRed else ScannerRed.copy(alpha = 0.15f),
                    Offset(i * (w + gap), 0f),
                    Size(w, size.height)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(clockText(position), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = DashGrey)
            if (now.durationMs > 0) Text(clockText(now.durationMs), style = MaterialTheme.typography.labelSmall, color = DashGrey)
        }
    }
}

private fun clockText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(Locale.US, s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(Locale.US, s / 60, s % 60)
}
