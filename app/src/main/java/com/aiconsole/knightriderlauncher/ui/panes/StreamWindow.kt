package com.aiconsole.knightriderlauncher.ui.panes

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aiconsole.knightriderlauncher.obd.DrivingRules
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

enum class StreamSource(val label: String, val url: String) {
    YouTube("YouTube", "https://m.youtube.com/"),
    YouTubeMusic("YT Music", "https://music.youtube.com/")
}

/**
 * The home screen's streaming window: YouTube / YouTube Music web players in a WebView that
 * lives inside the map pane, so the apps never take over the screen. The WebView outlives the
 * window being shown, so flipping back to the map and returning doesn't reload the page.
 */
class StreamSession {
    /** Chosen site, or null while the picker is showing. */
    var source by mutableStateOf<StreamSource?>(null)
        private set

    /** Whether the window covers the map right now. */
    var visible by mutableStateOf(false)

    private var view: WebView? = null

    fun open(next: StreamSource) {
        source = next
        visible = true
        view?.loadUrl(next.url)
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
        source = null
        visible = false
    }

    fun webView(context: Context): WebView = view ?: createWebView(context).also {
        view = it
        source?.let { s -> it.loadUrl(s.url) }
    }

    @SuppressLint("SetJavaScriptEnabled") // The YouTube players are JavaScript apps; only YouTube/Google pages load here.
    private fun createWebView(context: Context) = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(android.graphics.Color.BLACK)
        webViewClient = object : WebViewClient() {
            // Keep web links in the window. Anything else (intent://, vnd.youtube:, market://) is how
            // the site tries to hand off to the full-screen app, so it's dropped.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                return scheme != "http" && scheme != "https"
            }
        }
    }
}

/**
 * The window itself. While the vehicle is [moving] the video is covered (audio keeps playing):
 * moving video in the driver's view is a distraction and illegal in most places.
 */
@Composable
fun StreamWindow(session: StreamSession, obdMoving: Boolean, modifier: Modifier = Modifier) {
    val gpsMoving = rememberGpsMoving()
    val moving = obdMoving || gpsMoving == true
    val source = session.source

    KittPanel(
        title = source?.label ?: "Stream",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (source != null) {
                    KittButton("Back", { session.goBack() }, Modifier.height(32.dp).width(64.dp), DashAmber)
                    StreamSource.entries.filter { it != source }.forEach { other ->
                        KittButton(other.label, { session.open(other) }, Modifier.height(32.dp), DashAmber)
                    }
                }
                KittButton("Map", { session.visible = false }, Modifier.height(32.dp).width(64.dp), ScannerRed)
                KittButton("Close", { session.close() }, Modifier.height(32.dp).width(72.dp), DashGrey)
            }
        }
    ) {
        if (source == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                StreamSource.entries.forEach { s ->
                    KittButton(s.label, { session.open(s) }, Modifier.fillMaxWidth().height(64.dp), ScannerRed, active = true)
                }
                HelpText(
                    "Plays inside this window. Video is hidden while the vehicle is moving; audio keeps playing. " +
                        "Google sign-in isn't available inside the launcher, so it plays signed out."
                )
            }
            return@KittPanel
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    session.webView(context).also { (it.parent as? ViewGroup)?.removeView(it) }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (moving) {
                Column(
                    Modifier.fillMaxSize().background(KittBlack).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VoiceModulator(active = true, modifier = Modifier.fillMaxWidth().height(80.dp))
                    Text(
                        "VIDEO HIDDEN WHILE DRIVING",
                        style = MaterialTheme.typography.titleMedium,
                        color = ScannerRed,
                        textAlign = TextAlign.Center
                    )
                    Text("Audio keeps playing.", style = MaterialTheme.typography.bodyMedium, color = DashGrey)
                }
            } else if (gpsMoving == null && !obdMoving) {
                Text(
                    "NO SPEED SOURCE: CONNECT OBD OR ALLOW LOCATION SO VIDEO HIDES WHILE DRIVING",
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(KittBlack.copy(alpha = 0.8f)).padding(4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = DashAmber,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * GPS speed as a second opinion on movement, for when OBD isn't connected. Null when location
 * isn't allowed or there's no GPS, i.e. no answer rather than "parked".
 */
@Composable
private fun rememberGpsMoving(): Boolean? {
    val context = LocalContext.current
    var moving by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(LocationManager::class.java)
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (manager == null || !allowed || !manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            moving = null
            return@DisposableEffect onDispose { }
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val kmh = if (location.hasSpeed()) location.speed * 3.6 else null
                moving = DrivingRules.nextMoving(moving == true, kmh)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) {
                moving = null
            }
        }
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            moving = false
        } catch (_: SecurityException) {
            moving = null
        }
        onDispose { manager.removeUpdates(listener) }
    }
    return moving
}
