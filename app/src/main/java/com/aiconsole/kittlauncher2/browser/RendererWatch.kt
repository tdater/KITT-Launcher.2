package com.aiconsole.kittlauncher2.browser

import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import com.aiconsole.kittlauncher2.data.FreezeWatchdog
import java.util.WeakHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** When a busy page's renderer is shut down (pure, unit-tested). */
object RendererRules {
    /** A renderer busy this long is hung, not just a heavy page loading on a slow unit. */
    const val HUNG_MS = 20_000L

    /** The launcher's own UI thread stuck this long: the page is freezing the launcher, act now. */
    const val LAUNCHER_STALL_MS = 3_000L

    fun shouldTerminate(unresponsiveForMs: Long, launcherStalledMs: Long): Boolean =
        unresponsiveForMs >= HUNG_MS || launcherStalledMs >= LAUNCHER_STALL_MS

    /**
     * The freeze watchdog's own call, independent of WebView's unresponsive callback (which needs
     * the UI thread to be dispatching input, so it can stay silent exactly when the UI is stuck):
     * once per stall, as soon as the UI thread has been stuck [LAUNCHER_STALL_MS].
     */
    fun shouldKillOnStall(launcherStalledMs: Long, alreadyKilledThisStall: Boolean): Boolean =
        launcherStalledMs >= LAUNCHER_STALL_MS && !alreadyKilledThisStall
}

/**
 * Stops a hung web page from taking the whole launcher down.
 *
 * Android's WebView draws in lock-step with the page's renderer, so a page stuck in heavy
 * script (a long Claude conversation, a runaway web app) can freeze the launcher's UI thread
 * until Android declares it not responding and falls back to the stock launcher. WebView
 * reports an unresponsive renderer on a background thread, about every 5 s while it stays busy.
 * Heavy web apps (YouTube Music) are routinely busy that long while loading on head-unit
 * hardware, so the renderer is only killed once the launcher itself is stalling or the page has
 * been stuck for [RendererRules.HUNG_MS]. Every page it hosted then gets onRenderProcessGone,
 * shows a "page stopped" card, and reloads on request.
 */
object RendererWatch {

    /** Callbacks run here, never on the (possibly blocked) main thread. */
    val executor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "kitt-renderer-watch").apply { isDaemon = true } }

    private const val TAG = "RendererWatch"

    /** Renderers of the app's WebViews. Registered on the main thread, terminated from any thread. */
    private val renderers = WeakHashMap<WebViewRenderProcess, Boolean>()

    /** What this WebView can do, for the freeze report. */
    @Volatile var status: String = "WebView: not used yet"
        private set

    /** Whether this WebView can shut a frozen page's renderer down. */
    fun canTerminate(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)

    /** Call on the main thread when a WebView is created. */
    fun track(web: WebView) {
        val version = runCatching { WebViewCompat.getCurrentWebViewPackage(web.context)?.versionName }.getOrNull()
        val canTerminate = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)
        val canWatch = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)
        status = "WebView $version, renderer watch=$canWatch, can terminate=$canTerminate"
        if (!canTerminate) return
        val renderer = runCatching { WebViewCompat.getWebViewRenderProcess(web) }.getOrNull() ?: return
        synchronized(renderers) { renderers[renderer] = true }
    }

    /**
     * The UI thread is stuck (called by the freeze watchdog, off the main thread): shut every web
     * renderer down. The UI thread is waiting on one of them, so it comes back, and each tab shows
     * its "page stopped" card through onRenderProcessGone.
     */
    fun terminateAll() {
        val all = synchronized(renderers) { renderers.keys.toList() }
        Log.w(TAG, "Launcher UI stalled: terminating ${all.size} web renderer(s). $status")
        all.forEach { r -> runCatching { r.terminate() }.onFailure { Log.w(TAG, "Couldn't terminate a renderer", it) } }
    }

    /** When each page first went unresponsive. Only touched on [executor]. */
    private val since = WeakHashMap<WebView, Long>()

    val client = object : WebViewRenderProcessClient() {
        override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
            // Don't touch the view here: we're off the main thread.
            val now = SystemClock.uptimeMillis()
            val busyFor = now - since.getOrPut(view) { now - FIRST_REPORT_MS }
            val stalled = FreezeWatchdog.mainStalledMs()
            if (!RendererRules.shouldTerminate(busyFor, stalled)) {
                Log.i(TAG, "Web page busy for ${busyFor / 1000} s (launcher fine); letting it finish")
                return
            }
            if (renderer != null && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
                Log.w(TAG, "Web page renderer hung (${busyFor / 1000} s, launcher stalled ${stalled / 1000} s): terminating it")
                since.remove(view)
                runCatching { renderer.terminate() }.onFailure { Log.w(TAG, "Couldn't terminate the renderer", it) }
            } else {
                Log.w(TAG, "Web page renderer unresponsive (this WebView can't terminate it)")
            }
        }

        override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) {
            since.remove(view)
            Log.i(TAG, "Web page renderer responsive again")
        }
    }

    /** WebView's first report comes after the renderer has already been busy about this long. */
    private const val FIRST_REPORT_MS = 5_000L
}
