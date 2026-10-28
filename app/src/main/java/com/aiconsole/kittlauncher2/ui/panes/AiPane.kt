package com.aiconsole.kittlauncher2.ui.panes

import com.aiconsole.kittlauncher2.assistant.AssistantPrompt
import com.aiconsole.kittlauncher2.assistant.LauncherBus
import com.aiconsole.kittlauncher2.assistant.LauncherEvent
import com.aiconsole.kittlauncher2.BuildConfig
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.kittlauncher2.ui.theme.DashWhite
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.assistant.AiAppHandoff
import com.aiconsole.kittlauncher2.assistant.SpeechState
import com.aiconsole.kittlauncher2.assistant.SpeechToTextHelper
import com.aiconsole.kittlauncher2.browser.AiSites
import com.aiconsole.kittlauncher2.browser.BrowserSession
import com.aiconsole.kittlauncher2.stream.StreamSite
import com.aiconsole.kittlauncher2.ui.browser.BrowserView
import com.aiconsole.kittlauncher2.ui.browser.SitePicker
import com.aiconsole.kittlauncher2.ui.browser.SiteSection
import com.aiconsole.kittlauncher2.ui.components.KittButton
import com.aiconsole.kittlauncher2.ui.components.KittPanel
import com.aiconsole.kittlauncher2.ui.components.KittTextField
import com.aiconsole.kittlauncher2.ui.components.VoiceModulator
import com.aiconsole.kittlauncher2.ui.theme.DashAmber
import com.aiconsole.kittlauncher2.ui.theme.DashGrey
import com.aiconsole.kittlauncher2.ui.theme.LedGreen
import com.aiconsole.kittlauncher2.ui.theme.ScannerRed

/**
 * AI page: the driver's own chat AI accounts (Gemini, Claude, Perplexity, ChatGPT) in sandboxed
 * browser tabs, side by side if wanted. KITT's mic types a spoken prompt into every open AI tab
 * and sends it; "Read reply" speaks the latest answer. No API credits involved.
 */
@Composable
fun AiPane(session: BrowserSession, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val speech = remember { SpeechToTextHelper(context, app.settings, app.offlineSpeech) }
    DisposableEffect(speech) { onDispose { speech.release() } }
    val speechState by speech.state.collectAsState()
    var prompt by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var bigText by remember { mutableStateOf<String?>(null) }

    fun sendToAll(text: String) {
        val targets = session.tabs.filter { AiSites.forHost(it.host) != null && it.web != null }
        if (targets.isEmpty()) {
            status = "Open an AI site first (and sign in once)."
            return
        }
        targets.forEach { tab -> session.runIn(tab, AiSites.sendPrompt(text)) }
        status = "Sent to ${targets.joinToString { AiSites.forHost(it.host)!!.label }}."
    }

    LaunchedEffect(speechState) {
        val result = speechState as? SpeechState.Result ?: return@LaunchedEffect
        speech.reset()
        sendToAll(result.text)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start() else status = "Microphone permission is needed for voice prompts."
    }
    fun mic() {
        when {
            speechState is SpeechState.Listening -> speech.stop()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                app.kittVoice.stop()
                speech.start()
            }
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    KittPanel(title = "AI", modifier = modifier, accent = ScannerRed) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (BuildConfig.HOLO_DESIGN) {
                com.aiconsole.kittlauncher2.ui.components.SkinTerminal(Modifier.fillMaxWidth().height(96.dp))
                com.aiconsole.kittlauncher2.ui.components.EcuCards(Modifier.fillMaxWidth().height(64.dp))
                // KITT co-pilot shortcuts. All are read-only questions; clearing codes stays a manual
                // action on the Diagnostics screen.
                Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun askKitt(text: String) {
                        app.assistantInbox.value = AssistantPrompt(text, diagnostic = true)
                        LauncherBus.send(LauncherEvent.OpenAssistant)
                    }
                    KittButton("Diagnose ECU", { askKitt("Run a diagnosis on the engine and tell me what you see in the live data.") }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
                    KittButton("Read codes", { askKitt("Read my stored fault codes and explain what each one means.") }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
                    KittButton("Range report", { askKitt("Give me a range report: fuel level, how far I can go, and the nearest gas.") }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
                }
            }
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val listening = speechState is SpeechState.Listening
                KittButton(if (listening) "Stop" else "🎤 Speak", ::mic, Modifier.width(110.dp).fillMaxHeight(), ScannerRed, active = true)
                if (listening) {
                    VoiceModulator(active = true, modifier = Modifier.width(80.dp).fillMaxHeight())
                }
                KittTextField(prompt, { prompt = it }, "Ask every open AI…", Modifier.weight(1f), onDone = {
                    if (prompt.isNotBlank()) {
                        sendToAll(prompt)
                        prompt = ""
                    }
                })
                KittButton("Send", {
                    if (prompt.isNotBlank()) {
                        sendToAll(prompt)
                        prompt = ""
                    }
                }, Modifier.width(80.dp).fillMaxHeight(), LedGreen, enabled = prompt.isNotBlank())
                KittButton("Read reply", {
                    val tab = session.current ?: return@KittButton
                    session.runIn(tab, AiSites.readLatest(AiSites.forHost(tab.host))) { reply ->
                        if (reply.isBlank()) status = "Couldn't find a reply on this page." else app.kittVoice.speak(reply)
                    }
                }, Modifier.width(110.dp).fillMaxHeight(), DashAmber)
                KittButton("Big text", {
                    val tab = session.current ?: return@KittButton
                    session.runIn(tab, AiSites.readLatest(AiSites.forHost(tab.host))) { reply ->
                        if (reply.isBlank()) status = "Couldn't find a reply on this page." else bigText = reply
                    }
                }, Modifier.width(100.dp).fillMaxHeight(), LedGreen)
            }
            val line = when (val s = speechState) {
                is SpeechState.Listening -> s.partial.ifEmpty { "LISTENING…" }
                SpeechState.Processing -> "TRANSCRIBING…"
                is SpeechState.Error -> s.message
                else -> status
            }
            line?.let {
                Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = DashGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val reading = bigText
            if (reading != null) {
                BigTextReader(reading, onRead = { app.kittVoice.speak(reading) }, onClose = {
                    app.kittVoice.stop()
                    bigText = null
                }, modifier = Modifier.fillMaxWidth().weight(1f))
            } else {
                BrowserView(
                    session = session,
                    bookmarks = app.settings.aiBookmarks,
                    picker = { AiPicker(app, it) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AiPicker(app: KittApplication, session: BrowserSession) {
    SitePicker(
        session = session,
        bookmarks = app.settings.aiBookmarks,
        sections = listOf(SiteSection("Open an AI", AiSites.ALL.map { StreamSite(it.label, it.url) })),
        help = "Sign in once with your own Gemini, Claude, Perplexity or ChatGPT account; this page keeps its own sandboxed " +
            "sign-ins. Turn on SPLIT under MORE to see two at once. SPEAK types your words into every open AI tab and sends " +
            "them. These sites change their layouts, so sending or reading aloud can occasionally miss.",
        extra = {
            KittButton("Open all four as tabs", {
                AiSites.ALL.forEachIndexed { i, site ->
                    if (i == 0) session.open(site.url, site.label) else session.newTab(site.url, site.label)
                }
                session.select(0)
            }, Modifier.fillMaxWidth().height(56.dp), LedGreen, active = true)
            // The official apps are the fallback when a site is slow or won't sign in on this unit.
            val context = LocalContext.current
            val apps = remember { AiAppHandoff.installed(context) }
            if (apps.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    apps.forEach { ai ->
                        KittButton("${ai.label} app", { AiAppHandoff.open(context, ai) }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
                    }
                }
            }
        }
    )
}

/** The latest AI reply in big, glanceable text, with read-aloud. */
@Composable
private fun BigTextReader(text: String, onRead: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Read aloud", onRead, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = true)
            KittButton("Back to the page", onClose, Modifier.weight(1f).fillMaxHeight(), DashGrey)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(text.lines().filter { it.isNotBlank() }) { paragraph ->
                Text(
                    paragraph,
                    Modifier.padding(bottom = 14.dp),
                    style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 30.sp, lineHeight = 40.sp),
                    color = DashWhite
                )
            }
        }
    }
}
