package com.aiconsole.kittlauncher2.ui.panes

import android.media.AudioManager
import android.view.KeyEvent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.data.LauncherApp
import com.aiconsole.kittlauncher2.ui.components.AppIcon
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.components.VoiceModulator
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import com.aiconsole.kittlauncher2.ui.theme.KittBezel
import com.aiconsole.kittlauncher2.ui.theme.KittBlack
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed

/**
 * Replaces the app grid while moving: big media and volume controls, a KITT button and the top favourite
 * apps, so everything is a large, glanceable target.
 */
@Composable
fun DrivingPanel(
    favorites: List<LauncherApp>,
    onAppClick: (LauncherApp) -> Unit,
    onKitt: () -> Unit,
    kittSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val now by app.media.nowPlaying.collectAsState()
    fun key(code: Int) {
        val current = now
        when {
            current != null && code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> app.media.playPause()
            current != null && code == KeyEvent.KEYCODE_MEDIA_NEXT -> app.media.next()
            current != null && code == KeyEvent.KEYCODE_MEDIA_PREVIOUS -> app.media.previous()
            else -> context.getSystemService(AudioManager::class.java)?.let {
                it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        }
    }

    fun volume(direction: Int) {
        context.getSystemService(AudioManager::class.java)
            ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    KittPanel(title = "Driving",modifier = modifier, accent = ScannerRed, trailing = {
        now?.title?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton("Prev", { key(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
                KittButton(
                    if (now?.playing == true) "Pause" else "Play",
                    { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                    Modifier.weight(1.3f).fillMaxHeight(),
                    ScannerRed,
                    active = now?.playing == true
                )
                KittButton("Next", { key(KeyEvent.KEYCODE_MEDIA_NEXT) }, Modifier.weight(1f).fillMaxHeight(), ScannerRed)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KittButton("Vol −", { volume(AudioManager.ADJUST_LOWER) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
                Box(
                    Modifier.weight(1.3f).fillMaxHeight().clip(MaterialTheme.shapes.small).background(KittBlack)
                        .border(1.dp, ScannerRed, MaterialTheme.shapes.small).clickable(onClick = onKitt).padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VoiceModulator(active = kittSpeaking, modifier = Modifier.fillMaxSize())
                    Text("KITT", style = MaterialTheme.typography.titleLarge, color = ScannerRed)
                }
                KittButton("Vol +", { volume(AudioManager.ADJUST_RAISE) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            }
            // Three favourites, icon and name: fewer and bigger than the app grid.
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                favorites.take(3).forEach { app ->
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.small).background(KittBlack)
                            .border(1.dp, KittBezel, MaterialTheme.shapes.small).clickable { onAppClick(app) }.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app, Modifier.size(56.dp))
                        Text(app.label, style = MaterialTheme.typography.titleMedium, color = DashWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
