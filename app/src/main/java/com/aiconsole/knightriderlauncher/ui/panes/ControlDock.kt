package com.aiconsole.knightriderlauncher.ui.panes

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.SegmentedSlider
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashAmberDim
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.KittCharcoal
import com.aiconsole.knightriderlauncher.ui.theme.KittGrid
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.LedGreenDim
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import kotlin.math.roundToInt

/** Bottom pane: stationary dock with climate, media and vehicle shortcuts. */
@Composable
fun ControlDock(
    onMultiView: () -> Unit,
    onAssistant: () -> Unit,
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier,
    kittSpeaking: Boolean = false
) {
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
            ClimateSection(Modifier.weight(1.3f).fillMaxHeight())
            MediaSection(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onMediaKey = { sendMediaKey(context, it) }
            )
            VehicleSection(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onSystemSettings = { openSettings(context, Settings.ACTION_SETTINGS) },
                onMultiView = onMultiView,
                onDiagnostics = onDiagnostics,
                onConfig = onConfig
            )
            AssistantSection(onAssistant, kittSpeaking, Modifier.weight(0.5f).fillMaxHeight())
        }
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
            text = "KITT",
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

// TODO: route these to the vehicle HVAC bus (e.g. android.car CarPropertyManager) when available.
@Composable
private fun ClimateSection(modifier: Modifier = Modifier) {
    var driver by rememberSaveable { mutableFloatStateOf(0.48f) }
    var fan by rememberSaveable { mutableFloatStateOf(0.43f) }
    var passenger by rememberSaveable { mutableFloatStateOf(0.48f) }

    DockSection("Climate", DashAmber, modifier) {
        ClimateRow("DRV", driver, { driver = it }, "${toFahrenheit(driver)}°", DashAmber, DashAmberDim, Modifier.weight(1f))
        ClimateRow("FAN", fan, { fan = it }, "${(fan * 7).roundToInt()}", LedGreen, LedGreenDim, Modifier.weight(1f))
        ClimateRow("PASS", passenger, { passenger = it }, "${toFahrenheit(passenger)}°", DashAmber, DashAmberDim, Modifier.weight(1f))
    }
}

private fun toFahrenheit(value: Float): Int = 60 + (value * 25).roundToInt()

@Composable
private fun ClimateRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    readout: String,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(34.dp), style = MaterialTheme.typography.labelSmall, color = DashGrey)
        SegmentedSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(0.7f),
            segments = 14,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )
        Text(
            text = readout,
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = activeColor,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

@Composable
private fun MediaSection(onMediaKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Local play/pause indicator only; hook a MediaController session for true playback state.
    var playing by rememberSaveable { mutableStateOf(false) }

    DockSection("Media", ScannerRed, modifier) {
        VoiceModulator(
            active = playing,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KittButton(
                label = "Prev",
                onClick = { onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = ScannerRed
            )
            KittButton(
                label = if (playing) "Pause" else "Play",
                onClick = {
                    playing = !playing
                    onMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = ScannerRed,
                active = playing
            )
            KittButton(
                label = "Next",
                onClick = { onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = ScannerRed
            )
        }
    }
}

@Composable
private fun VehicleSection(
    onSystemSettings: () -> Unit,
    onMultiView: () -> Unit,
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    DockSection("Vehicle", LedGreen, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KittButton("Diagnostics", onDiagnostics, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = true)
            KittButton("Multi-View", onMultiView, Modifier.weight(1f).fillMaxHeight(), ScannerRed, active = true)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KittButton("KITT Config", onConfig, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton("System", onSystemSettings, Modifier.weight(1f).fillMaxHeight(), DashAmber)
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
