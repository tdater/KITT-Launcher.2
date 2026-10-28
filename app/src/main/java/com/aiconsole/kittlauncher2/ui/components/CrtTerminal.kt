package com.aiconsole.kittlauncher2.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.LocalLiteMode
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The assistant's curved CRT: a neon-bezelled screen with KITT's status on top, a live speech
 * waveform in the middle and the microphone state underneath. The waveform only moves while KITT
 * is listening, thinking or speaking; idle it is a flat line (and stands still in lite mode).
 */
@Composable
fun CrtTerminal(status: String, active: Boolean, level: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val phase = if (active && !LocalLiteMode.current) {
        rememberInfiniteTransition(label = "crt").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "crtPhase"
        )
    } else null
    Column(
        modifier.clip(shape).background(Color(0xFF0A0000)).border(3.dp, ScannerRed, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            status.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(shadow = Shadow(ScannerRed, Offset.Zero, 12f)),
            color = ScannerRed, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Canvas(Modifier.weight(1f).fillMaxWidth()) {
            val bars = 72
            val step = size.width / bars
            val mid = size.height / 2f
            val amp = if (active) 0.30f + (level ?: 0.5f).coerceIn(0f, 1f) * 0.70f else 0.03f
            val p = (phase?.value ?: 0f) * 2f * PI.toFloat()
            for (i in 0 until bars) {
                val envelope = sin(PI * (i + 0.5f) / bars).toFloat()
                val wobble = (abs(sin(i * 0.9f + p)) + abs(sin(i * 0.37f - p))) / 2f
                val h = mid * amp * envelope * (0.3f + 0.7f * wobble)
                val x = step * (i + 0.5f)
                drawLine(ScannerRed.copy(alpha = 0.35f), Offset(x, mid - h), Offset(x, mid + h), 4.dp.toPx(), StrokeCap.Round)
                drawLine(ScannerRed, Offset(x, mid - h), Offset(x, mid + h), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
        Text(if (active) "● MIC ACTIVE" else "○ MIC IDLE", style = MaterialTheme.typography.labelSmall, color = if (active) ScannerRed else DashGrey, maxLines = 1)
    }
}

/** An outlined pill for the actions under an answer: View Freeze Frame, Nearest Repair Shop, and so on. */
@Composable
fun ActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Text(
        label,
        modifier.clip(pill).border(1.5.dp, DashAmber, pill).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = DashAmber,
        maxLines = 1,
        softWrap = false
    )
}
