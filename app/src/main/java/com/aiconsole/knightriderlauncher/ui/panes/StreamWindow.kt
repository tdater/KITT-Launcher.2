package com.aiconsole.knightriderlauncher.ui.panes

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/** Quick-pick streaming sites; anything else can be typed in. */
enum class StreamSource(val label: String, val url: String) {
    YouTube("YouTube", "https://m.youtube.com/"),
    YouTubeMusic("YT Music", "https://music.youtube.com/")
}

/** Turns what the driver typed into a loadable address. */
object StreamUrls {
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        return when {
            text.startsWith("https://", ignoreCase = true) || text.startsWith("http://", ignoreCase = true) -> text
            '.' in text -> "https://$text"
            else -> null
        }
    }

    /** Short title for the window header, e.g. "twitch.tv". */
    fun titleOf(url: String): String =
        url.substringAfter("://").substringBefore('/').removePrefix("www.").removePrefix("m.").ifEmpty { "Stream" }
}

/**
 * The home screen's streaming window: web video players (YouTube, YouTube Music, or any site
 * the driver enters) in a WebView that lives inside the map pane, so nothing takes over the
 * screen. The WebView outlives the window being shown, so flipping back to the map and returning
 * doesn't reload the page.
 */
class StreamSession {
    /** Header title of the loaded site, or null while the site picker is showing. */
    var title by mutableStateOf<String?>(null)
        private set

    /** Whether the window covers the map right now. */
    var visible by mutableStateOf(false)

    private var url: String? = null
    private var view: WebView? = null

    fun open(address: String, label: String = StreamUrls.titleOf(address)) {
        url = address
        title = label
        visible = true
        view?.loadUrl(address)
    }

    /** Back to the site picker, keeping the window open. */
    fun pickSite() {
        title = null
    }

    fun canGoBack(): Boolean = view?.canGoBack() == true

    fun goBack() {
        view?.goBack()
    }

    fun close() {
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        view = null
        url = null
        title = null
        visible = false
    }

    fun webView(context: Context): WebView = view ?: createWebView(context).also {
        view = it
        url?.let(it::loadUrl)
    }

    @SuppressLint("SetJavaScriptEnabled") // Web video players are JavaScript apps.
    private fun createWebView(context: Context) = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(android.graphics.Color.BLACK)
        webViewClient = object : WebViewClient() {
            // Keep web links in the window. Anything else (intent://, vnd.youtube:, market://) is how
            // a site hands off to its full-screen app, so it's dropped.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                return scheme != "http" && scheme != "https"
            }
        }
    }
}

@Composable
fun StreamWindow(session: StreamSession, modifier: Modifier = Modifier) {
    val title = session.title

    KittPanel(
        title = title ?: "Stream",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (title != null) {
                    KittButton("Back", { session.goBack() }, Modifier.height(32.dp).width(64.dp), DashAmber)
                    KittButton("Sites", { session.pickSite() }, Modifier.height(32.dp).width(72.dp), DashAmber)
                }
                KittButton("Map", { session.visible = false }, Modifier.height(32.dp).width(64.dp), ScannerRed)
                KittButton("Close", { session.close() }, Modifier.height(32.dp).width(72.dp), DashGrey)
            }
        }
    ) {
        if (title == null) {
            SitePicker(session)
        } else {
            AndroidView(
                factory = { context ->
                    session.webView(context).also { (it.parent as? ViewGroup)?.removeView(it) }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SitePicker(session: StreamSession) {
    var address by remember { mutableStateOf("") }
    val target = StreamUrls.normalize(address)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        StreamSource.entries.forEach { s ->
            KittButton(s.label, { session.open(s.url, s.label) }, Modifier.fillMaxWidth().height(64.dp), ScannerRed, active = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KittTextField(address, { address = it }, "Any site, e.g. twitch.tv", Modifier.weight(1f))
            KittButton(
                label = "Go",
                onClick = { target?.let { session.open(it) } },
                modifier = Modifier.height(48.dp).width(72.dp),
                color = ScannerRed,
                active = target != null,
                enabled = target != null
            )
        }
        HelpText(
            "Plays inside this window; links that would open a full-screen app are blocked. " +
                "Google sign-in isn't available inside the launcher, and some paid services block embedded browsers."
        )
    }
}
