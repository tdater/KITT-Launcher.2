package com.aiconsole.kittlauncher2.ui.browser

import android.net.Uri
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aiconsole.kittlauncher2.browser.MediaUrls
import com.aiconsole.kittlauncher2.stream.StreamUrls
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.theme.AlertRed
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlinx.coroutines.delay

private const val SKIP_MS = 10_000L

/**
 * Plays a direct video link (mp4, webm, mkv, HLS, DASH) with Media3 instead of a web page. It
 * draws on a plain SurfaceView and has big touch/D-pad buttons, so it never goes through the
 * WebView's compositor. If the stream won't play, "Open in page" hands the link back to the browser.
 */
@Composable
fun NativePlayer(url: String, onClose: () -> Unit, onOpenInPage: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var error by remember(url) { mutableStateOf<String?>(null) }
    var playing by remember(url) { mutableStateOf(true) }
    var buffering by remember(url) { mutableStateOf(true) }
    val position = remember(url) { mutableLongStateOf(0L) }
    val duration = remember(url) { mutableLongStateOf(0L) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            // Takes audio focus like any media app, so other sound pauses and a call or the voice assistant pauses this.
            setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                }

                override fun onPlayerError(e: PlaybackException) {
                    error = e.errorCodeName
                }
            })
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player) {
        while (true) {
            position.longValue = player.currentPosition
            duration.longValue = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(500)
        }
    }

    Column(modifier.background(KittBlack), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx -> SurfaceView(ctx).also { player.setVideoSurfaceView(it) } },
                modifier = Modifier.fillMaxSize()
            )
            when {
                error != null -> Text(
                    "This video won't play here (${error}). Try Open in page.",
                    style = MaterialTheme.typography.bodyMedium, color = AlertRed, modifier = Modifier.padding(16.dp)
                )
                buffering -> Text("LOADING…", style = MaterialTheme.typography.labelLarge, color = DashAmber)
            }
        }
        Text(StreamUrls.titleOf(url), style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
        // Tap the bar to jump to that point.
        Canvas(
            Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 8.dp).pointerInput(player) {
                detectTapGestures { at ->
                    val total = player.duration
                    if (total != C.TIME_UNSET && size.width > 0) player.seekTo((total * (at.x / size.width)).toLong())
                }
            }
        ) {
            drawRect(DashGrey.copy(alpha = 0.35f), Offset(0f, size.height / 2 - 2.dp.toPx()), Size(size.width, 4.dp.toPx()))
            val frac = if (duration.longValue > 0) (position.longValue.toFloat() / duration.longValue).coerceIn(0f, 1f) else 0f
            drawRect(ScannerRed, Offset(0f, size.height / 2 - 2.dp.toPx()), Size(size.width * frac, 4.dp.toPx()))
        }
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KittButton("−10s", { player.seekTo((player.currentPosition - SKIP_MS).coerceAtLeast(0L)) }, Modifier.width(72.dp).fillMaxSize(), DashAmber)
            KittButton(if (playing) "Pause" else "Play", { if (player.isPlaying) player.pause() else player.play() }, Modifier.width(88.dp).fillMaxSize(), LedGreen, active = true)
            KittButton("+10s", { player.seekTo(player.currentPosition + SKIP_MS) }, Modifier.width(72.dp).fillMaxSize(), DashAmber)
            Text(
                "${MediaUrls.clock(position.longValue)} / ${MediaUrls.clock(duration.longValue)}",
                style = MaterialTheme.typography.labelLarge, color = DashWhite, modifier = Modifier.weight(1f).padding(horizontal = 6.dp), maxLines = 1
            )
            KittButton("Open in page", onOpenInPage, Modifier.width(120.dp).fillMaxSize(), DashGrey)
            KittButton("Close", onClose, Modifier.width(80.dp).fillMaxSize(), ScannerRed)
        }
    }
}
