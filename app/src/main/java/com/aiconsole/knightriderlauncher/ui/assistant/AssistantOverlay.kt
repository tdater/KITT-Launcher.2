package com.aiconsole.knightriderlauncher.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiconsole.knightriderlauncher.assistant.AiAppHandoff
import com.aiconsole.knightriderlauncher.assistant.AssistantResult
import com.aiconsole.knightriderlauncher.assistant.AssistantViewModel
import com.aiconsole.knightriderlauncher.assistant.Exchange
import com.aiconsole.knightriderlauncher.assistant.SpeechState
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ReadoutFont
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

// Large sans-serif for answers: readable at a glance from the driver's seat.
private val AnswerStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp)

/** KITT assistant overlay: voice visualizer, answer cards, minimal text input and big voice controls. */
@Composable
fun AssistantOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoListen: Boolean = false,
    viewModel: AssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val handoffApps = remember { AiAppHandoff.installed(context) }
    val speechState by viewModel.speech.state.collectAsState()
    val exchanges by viewModel.exchanges.collectAsState()
    val thinking by viewModel.thinking.collectAsState()
    val speaking by viewModel.speaking.collectAsState()
    val voiceReply by viewModel.voiceReply.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startListening() else notice = "Microphone permission denied. Type your question instead."
    }

    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) { onDispose { viewModel.silence() } }
    LaunchedEffect(exchanges.size, exchanges.lastOrNull()?.result) {
        if (exchanges.isNotEmpty()) listState.animateScrollToItem(exchanges.lastIndex)
    }

    val listening = speechState as? SpeechState.Listening
    val status = when {
        listening != null -> listening.partial.ifEmpty { "LISTENING…" }
        speechState is SpeechState.Processing -> "PROCESSING…"
        thinking -> "THINKING…"
        speaking -> "SPEAKING"
        speechState is SpeechState.Error -> (speechState as SpeechState.Error).message
        notice != null -> notice.orEmpty()
        else -> "TAP SPEAK OR TYPE A QUESTION"
    }

    fun submitText() {
        if (input.isNotBlank()) {
            notice = null
            viewModel.ask(input)
            input = ""
        }
    }

    fun toggleMic() {
        notice = null
        when {
            listening != null -> viewModel.stopListening()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.startListening()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Steering-wheel voice button: open straight into listening.
    LaunchedEffect(autoListen) {
        if (autoListen && listening == null) toggleMic()
    }

    KittPanel(
        title = "KITT",
        modifier = modifier.pointerInput(Unit) { detectTapGestures {} },
        accent = ScannerRed,
        trailing = {
            Text(
                text = if (voiceReply) "VOICE REPLY ON" else "VOICE REPLY OFF",
                style = MaterialTheme.typography.labelMedium,
                color = if (voiceReply) LedGreen else DashGrey
            )
        }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceModulator(
                active = listening != null || thinking || speaking,
                level = listening?.level,
                modifier = Modifier.fillMaxWidth().height(110.dp)
            )
            Text(
                text = status,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = if (speechState is SpeechState.Error || notice != null) DashAmber else ScannerRed,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(exchanges, key = { it.id }) { ExchangeCard(it) }
            }

            if (handoffApps.isNotEmpty()) {
                // Use the driver's own subscriptions via the official apps.
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ASK IN APP", style = MaterialTheme.typography.labelSmall, color = DashGrey)
                    handoffApps.forEach { handoff ->
                        KittButton(
                            label = handoff.label,
                            onClick = {
                                val question = input.ifBlank { exchanges.lastOrNull()?.prompt.orEmpty() }
                                AiAppHandoff.send(context, handoff, question)
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            color = DashAmber
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PromptField(
                    value = input,
                    onValueChange = { input = it },
                    onSubmit = ::submitText,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                KittButton(
                    label = "Send",
                    onClick = ::submitText,
                    modifier = Modifier.weight(0.3f).fillMaxHeight(),
                    color = DashAmber,
                    enabled = input.isNotBlank() && !thinking
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(68.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KittButton(
                    label = if (listening != null) "Stop" else "Speak",
                    onClick = ::toggleMic,
                    modifier = Modifier.weight(2f).fillMaxHeight(),
                    color = ScannerRed,
                    active = true,
                    enabled = !thinking
                )
                KittButton(
                    label = if (voiceReply) "Mute" else "Voice",
                    onClick = viewModel::toggleVoiceReply,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    color = LedGreen,
                    active = voiceReply
                )
                KittButton("Reset", viewModel::clearConversation, Modifier.weight(0.8f).fillMaxHeight(), DashAmber)
                KittButton("Close", onDismiss, Modifier.weight(1f).fillMaxHeight(), DashGrey)
            }
        }
    }
}

@Composable
private fun PromptField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(fontFamily = ReadoutFont, fontSize = 18.sp, color = DashAmber),
        cursorBrush = SolidColor(DashAmber),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(KittBlack)
                    .border(1.dp, KittBezel, shape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text("Ask KITT…", style = MaterialTheme.typography.bodyLarge, color = DashGrey)
                }
                inner()
            }
        }
    )
}

@Composable
private fun ExchangeCard(exchange: Exchange) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("YOU", style = MaterialTheme.typography.labelSmall, color = DashGrey)
        Text(exchange.prompt, style = MaterialTheme.typography.bodyLarge, color = DashWhite)
        when (val result = exchange.result) {
            null -> Text("KITT IS THINKING…", style = MaterialTheme.typography.labelMedium, color = DashAmber)
            is AssistantResult.Answer -> ResultCard(
                label = "KITT",
                labelColor = ScannerRed,
                borderColor = DashAmber,
                badge = when {
                    result.usedWebSearch -> "WEB SEARCH"
                    result.searchUnavailable -> "NO SEARCH ON FREE TIER"
                    else -> null
                },
                text = result.text,
                footer = listOfNotNull(
                    result.engine.takeIf { it.isNotEmpty() }?.uppercase(),
                    result.sources.takeIf { it.isNotEmpty() }?.take(3)?.joinToString(" · ") { it.title }
                ).joinToString("  ·  ").ifEmpty { null }
            )
            is AssistantResult.Failure -> ResultCard(
                label = if (result.offline) "OFFLINE" else "ERROR",
                labelColor = ScannerRed,
                borderColor = ScannerRed,
                badge = null,
                text = result.message,
                footer = null
            )
        }
    }
}

@Composable
private fun ResultCard(
    label: String,
    labelColor: Color,
    borderColor: Color,
    badge: String?,
    text: String,
    footer: String?
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, borderColor, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
            if (badge != null) {
                Text(
                    text = badge,
                    modifier = Modifier
                        .background(LedGreen.copy(alpha = 0.15f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = LedGreen
                )
            }
        }
        Text(text, style = AnswerStyle, color = DashWhite)
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = DashGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
