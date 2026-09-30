package com.aiconsole.knightriderlauncher.stream

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiconsole.knightriderlauncher.data.settings.KittSettings

/**
 * The home screen's streaming window: web video players (YouTube, YouTube Music, or any site the
 * driver enters) in a WebView that lives inside the map pane, so nothing takes over the screen.
 * The WebView outlives the window being shown, so flipping back to the map doesn't reload it.
 *
 * Extras: desktop-site mode, userscripts injected at document start, fullscreen requests shown
 * inside the pane, and `.user.js` links offered for install instead of opened.
 */
class StreamSession(private val settings: KittSettings, private val userscripts: UserscriptStore) {

    /** Header title of the loaded site, or null while the site picker is showing. */
    var title by mutableStateOf<String?>(null)
        private set

    /** Whether the window covers the map right now. */
    var visible by mutableStateOf(false)

    var desktop by mutableStateOf(settings.streamDesktop.value)
        private set

    /** A userscript link the driver tapped, awaiting install confirmation. */
    var pendingInstall by mutableStateOf<String?>(null)

    var showScripts by mutableStateOf(false)

    /** A site's fullscreen video view, shown inside the pane instead of the page. */
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var url: String? = null
    private var view: WebView? = null
    private var mobileUserAgent: String? = null
    private val handlers = mutableListOf<ScriptHandler>()

    fun open(address: String, label: String = StreamUrls.titleOf(address)) {
        url = address
        title = label
        visible = true
        showScripts = false
        view?.loadUrl(address)
    }

    /** The page showing now, for saving it as a one-tap site. */
    fun currentPage(): StreamSite? {
        val web = view ?: return null
        val address = web.url?.takeIf { it.startsWith("http") } ?: return null
        val label = web.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }?.take(40) ?: StreamUrls.titleOf(address)
        return StreamSite(label, address)
    }

    /** Back to the site picker, keeping the page loaded. */
    fun pickSite() {
        title = null
    }

    fun canGoBack(): Boolean = view?.canGoBack() == true

    fun goBack() {
        view?.goBack()
    }

    fun reload() {
        view?.reload()
    }

    fun setDesktopMode(on: Boolean) {
        desktop = on
        settings.streamDesktop.set(on)
        view?.let {
            applyUserAgent(it)
            it.reload()
        }
    }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreenView = null
    }

    fun close() {
        exitFullscreen()
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        handlers.clear()
        view = null
        url = null
        title = null
        visible = false
        showScripts = false
        pendingInstall = null
    }

    fun webView(context: Context): WebView = view ?: createWebView(context).also {
        view = it
        applyScripts(userscripts.scripts.value)
        url?.let(it::loadUrl)
    }

    /**
     * (Re)registers enabled scripts. With document-start support they run on every later page load
     * before the site's own code; older WebViews fall back to injecting when a page finishes.
     */
    fun applyScripts(scripts: List<InstalledScript>) {
        val web = view ?: return
        handlers.forEach { it.remove() }
        handlers.clear()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        scripts.filter { it.enabled }.forEach { installed ->
            try {
                handlers += WebViewCompat.addDocumentStartJavaScript(web, Userscripts.wrap(installed.script), setOf("*"))
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Couldn't register ${installed.script.name}", e)
            }
        }
    }

    private fun applyUserAgent(web: WebView) {
        val mobile = mobileUserAgent ?: web.settings.userAgentString.also { mobileUserAgent = it }
        web.settings.userAgentString = if (desktop) StreamUrls.desktopUserAgent(mobile) else mobile
        // Desktop layouts are laid out wide, then scaled to fit; pinch to zoom in.
        web.settings.useWideViewPort = desktop
        web.settings.loadWithOverviewMode = desktop
    }

    @SuppressLint("SetJavaScriptEnabled") // Web video players are JavaScript apps.
    private fun createWebView(context: Context) = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setBackgroundColor(android.graphics.Color.BLACK)
        applyUserAgent(this)
        webViewClient = StreamClient()
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenCallback?.onCustomViewHidden()
                fullscreenView = view
                fullscreenCallback = callback
            }

            override fun onHideCustomView() {
                fullscreenView = null
                fullscreenCallback = null
            }
        }
    }

    // onRenderProcessGone IS implemented below; androidx.webkit's check misfires on Kotlin subclasses
    // because it flags the WebViewClient() supertype constructor call.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class StreamClient : WebViewClient() {
        // Keep web links in the window. Other schemes (intent://, vnd.youtube:, market://) are how
        // a site hands off to its full-screen app, so they're dropped. Userscripts get a prompt.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            if (StreamUrls.isUserscript(target)) {
                pendingInstall = target
                return true
            }
            val scheme = request.url.scheme
            return scheme != "http" && scheme != "https"
        }

        // A crashed or killed page renderer must not take the launcher down with it: drop the
        // WebView; tapping a site again starts a fresh one.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Stream renderer gone; closing the window")
            close()
            return true
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
            // Fallback injection; the wrapper checks the URL and runs each script once per page.
            userscripts.scripts.value.filter { it.enabled }.forEach {
                view.evaluateJavascript(Userscripts.wrap(it.script), null)
            }
        }
    }

    private companion object {
        const val TAG = "StreamSession"
    }
}
