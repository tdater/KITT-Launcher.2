package com.aiconsole.knightriderlauncher.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.stream.InstalledScript
import com.aiconsole.knightriderlauncher.stream.StreamUrls
import com.aiconsole.knightriderlauncher.stream.UserscriptStore
import com.aiconsole.knightriderlauncher.stream.Userscripts
import java.io.ByteArrayInputStream

/** One browser tab: its page and, while it exists, its WebView. */
class BrowserTab(val id: Long, initialUrl: String?, initialTitle: String?) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)

    /** 0..100 while loading, 100 when done. */
    var progress by mutableIntStateOf(100)

    /** The new-tab page (site picker) instead of a website. */
    val isPicker: Boolean get() = url == null
    var web: WebView? = null
    var desktop: Boolean? = null
    internal val handlers = mutableListOf<ScriptHandler>()

    val host: String? get() = url?.let { runCatching { it.toUri().host }.getOrNull() }
}

/**
 * A tabbed, sandboxed web browser used by the stream window, the AI page and the Games page.
 * Each [space] gets its own WebView profile (cookies, storage, sign-ins) where the WebView
 * supports it, like a Hermit / Native Alpha web app. Sites get per-host options ([SiteConfig]):
 * desktop or mobile layout, dark pages, ad/tracker blocking and text size.
 */
class BrowserSession(
    private val space: String,
    private val settings: KittSettings,
    private val userscripts: UserscriptStore
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var currentIndex by mutableIntStateOf(0)

    /** For the stream window: whether it's covering the map. */
    var visible by mutableStateOf(false)

    /** Show two tabs side by side (the AI page's tandem mode). */
    var split by mutableStateOf(false)

    var pendingInstall by mutableStateOf<String?>(null)
    var showScripts by mutableStateOf(false)
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /** A site asked for a file (e.g. a ROM); the UI shows the system picker and calls [deliverFiles]. */
    var fileRequest by mutableStateOf<Intent?>(null)
        private set
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private var nextId = 1L
    private val profile = "kitt-$space"

    val current: BrowserTab? get() = tabs.getOrNull(currentIndex)

    fun newTab(url: String? = null, title: String? = null): BrowserTab {
        val tab = BrowserTab(nextId++, url, title)
        tabs += tab
        currentIndex = tabs.lastIndex
        return tab
    }

    /** Opens [url] in the current tab (a new one if there's none). */
    fun open(url: String, title: String = StreamUrls.titleOf(url)) {
        val tab = current ?: newTab()
        tab.url = url
        tab.title = title
        tab.web?.let { web ->
            applySiteOptions(tab, web)
            web.loadUrl(url)
        }
        visible = true
        showScripts = false
    }

    fun select(index: Int) {
        if (index in tabs.indices) currentIndex = index
    }

    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        destroy(tab)
        tabs.removeAt(index)
        currentIndex = currentIndex.coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
    }

    /** Back to the site picker in the current tab. */
    fun showPicker() {
        current?.let { tab ->
            destroy(tab)
            tab.url = null
            tab.title = null
        }
    }

    fun canGoBack(): Boolean = current?.web?.canGoBack() == true
    fun goBack() = current?.web?.goBack()
    fun reload() = current?.web?.reload()

    fun run(script: String, onResult: (String) -> Unit = {}) {
        current?.web?.evaluateJavascript(script) { onResult(it.orEmpty().trim('"').replace("\\n", "\n")) }
    }

    fun runIn(tab: BrowserTab, script: String, onResult: (String) -> Unit = {}) {
        tab.web?.evaluateJavascript(script) { onResult(it.orEmpty().trim('"').replace("\\n", "\n")) }
    }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreenView = null
    }

    fun deliverFiles(uris: Array<Uri>?) {
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
        fileRequest = null
    }

    /** Closes every tab and frees the WebViews. */
    fun close() {
        exitFullscreen()
        deliverFiles(null)
        tabs.forEach(::destroy)
        tabs.clear()
        currentIndex = 0
        visible = false
        showScripts = false
        pendingInstall = null
    }

    // ---- Site options ------------------------------------------------------------------

    fun siteConfig(tab: BrowserTab?): SiteConfig? = tab?.host?.let { SiteSettings.decode(settings.siteSettings.value)[SiteSettings.keyFor(it)] }

    fun isDesktop(tab: BrowserTab): Boolean = siteConfig(tab)?.desktop ?: settings.streamDesktop.value
    fun isDark(tab: BrowserTab): Boolean = siteConfig(tab)?.dark ?: settings.darkPages.value
    fun blocksAds(tab: BrowserTab): Boolean =
        SiteSettings.blockAllowed(tab.host) && (siteConfig(tab)?.blockAds ?: settings.blockAds.value)
    fun textZoom(tab: BrowserTab): Int = siteConfig(tab)?.textZoom ?: 100

    /** Changes this site's options, remembers them, and re-applies (reloading if the layout changed). */
    fun updateSite(change: (SiteConfig) -> SiteConfig) {
        val tab = current ?: return
        val host = tab.host ?: return
        settings.siteSettings.set(SiteSettings.encode(SiteSettings.update(SiteSettings.decode(settings.siteSettings.value), host, change)))
        tab.web?.let { if (applySiteOptions(tab, it)) it.reload() }
    }

    /** Returns true when the user agent changed (the page must reload to use it). */
    private fun applySiteOptions(tab: BrowserTab, web: WebView): Boolean {
        val desktop = isDesktop(tab)
        val changed = tab.desktop != null && tab.desktop != desktop
        tab.desktop = desktop
        val s = web.settings
        s.userAgentString = if (desktop) StreamUrls.desktopUserAgent(WebSettings.getDefaultUserAgent(web.context)) else null
        s.useWideViewPort = desktop
        s.loadWithOverviewMode = desktop
        s.textZoom = textZoom(tab)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, isDark(tab))
        }
        return changed
    }

    // ---- WebViews ----------------------------------------------------------------------

    /** The tab's WebView, created on first use. */
    fun webViewFor(context: Context, tab: BrowserTab): WebView = tab.web ?: createWebView(context, tab).also { web ->
        tab.web = web
        applyScripts(tab, userscripts.scripts.value)
        tab.url?.let(web::loadUrl)
    }

    fun applyScripts(scripts: List<InstalledScript>) = tabs.forEach { applyScripts(it, scripts) }

    private fun applyScripts(tab: BrowserTab, scripts: List<InstalledScript>) {
        val web = tab.web ?: return
        tab.handlers.forEach { it.remove() }
        tab.handlers.clear()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        scripts.filter { it.enabled }.forEach { installed ->
            try {
                tab.handlers += WebViewCompat.addDocumentStartJavaScript(web, Userscripts.wrap(installed.script), setOf("*"))
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Couldn't register ${installed.script.name}", e)
            }
        }
    }

    private fun destroy(tab: BrowserTab) {
        tab.handlers.clear()
        tab.web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        tab.web = null
        tab.desktop = null
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createWebView(context: Context, tab: BrowserTab): WebView = WebView(context).apply {
        // Sandbox: each space keeps its own cookies and sign-ins when the WebView supports profiles.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.setProfile(this, profile) }.onFailure { Log.w(TAG, "No separate profile for $space", it) }
        }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // Smoother scrolling on slower head units; renderer stays a priority while visible.
        settings.offscreenPreRaster = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        setBackgroundColor(android.graphics.Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        // Keep gestures in the page: parents (the home pager, scrolling panels) mustn't take them.
        setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                if (!v.hasFocus()) v.requestFocus()
            }
            false
        }
        applySiteOptions(tab, this)
        webViewClient = TabClient(tab)
        webChromeClient = TabChrome(tab)
        setDownloadListener { url, _, _, mimetype, _ ->
            if (StreamUrls.isUserscript(url) || mimetype == "text/x-userscript") pendingInstall = url
        }
    }

    // onRenderProcessGone IS implemented below; androidx.webkit's check misfires on Kotlin subclasses.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class TabClient(private val tab: BrowserTab) : WebViewClient() {
        // Web links stay in the tab. Other schemes (intent://, vnd.youtube:, market://) are how a
        // site hands off to its full-screen app, so they're dropped. Userscripts get a prompt.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            if (StreamUrls.isUserscript(target)) {
                pendingInstall = target
                return true
            }
            val scheme = request.url.scheme
            return scheme != "http" && scheme != "https"
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (url != null && url.startsWith("http")) {
                tab.url = url
                if (applySiteOptions(tab, view)) view.reload()
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            tab.title = view.title?.takeIf { it.isNotBlank() } ?: tab.title
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
            userscripts.scripts.value.filter { it.enabled }.forEach { view.evaluateJavascript(Userscripts.wrap(it.script), null) }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!blocksAds(tab) || !AdBlock.isBlocked(request.url.host)) return null
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        // A crashed or killed renderer must not take the launcher down: drop this tab's WebView;
        // it's recreated (and the page reloaded) the next time the tab is shown.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Renderer gone in $space tab ${tab.id}")
            if (tab.web === view) {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                tab.web = null
                tab.handlers.clear()
            }
            return true
        }
    }

    private inner class TabChrome(private val tab: BrowserTab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) tab.title = title
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenView = view
            fullscreenCallback = callback
        }

        override fun onHideCustomView() {
            fullscreenView = null
            fullscreenCallback = null
        }

        /** Pop-ups the driver triggered open as a new tab; unrequested ones are blocked. */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) return false
            val newTab = newTab(null, "New tab")
            newTab.url = "about:blank"
            val web = createWebView(view.context, newTab)
            newTab.web = web
            applyScripts(newTab, userscripts.scripts.value)
            (resultMsg.obj as? WebView.WebViewTransport)?.webView = web
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            tabs.indexOfFirst { it.web === window }.takeIf { it >= 0 }?.let(::closeTab)
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            fileRequest = runCatching { params.createIntent() }.getOrNull() ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
            return true
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            // Page errors go to the app log so "Report a problem" captures them.
            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                Log.w(TAG, "[$space] ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
            }
            return true
        }
    }

    private companion object {
        const val TAG = "Browser"
    }
}
