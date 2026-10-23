package com.aiconsole.kittlauncher2.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.aiconsole.kittlauncher2.data.ReturnHome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A music app that offers its library to other apps (Spotify, YouTube Music, USB players…). */
data class MediaSource(val packageName: String, val label: String, val service: ComponentName)

sealed interface LibraryStatus {
    data object Idle : LibraryStatus
    data object Connecting : LibraryStatus
    data object Loading : LibraryStatus
    data object Ready : LibraryStatus
    data class Failed(val message: String) : LibraryStatus
}

/**
 * Lets the Media page browse and search a music app's own library and start playback, so the
 * driver doesn't have to open the app. It talks to the app's MediaBrowserService (the interface
 * cars and Android Auto use), then plays through that app's media session. Only read-style calls
 * and play requests; nothing is written to the app. Apps decide who may browse them, so one that
 * refuses shows a message instead. Every callback runs on the main thread.
 */
class MediaLibrary(context: Context) {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _sources = MutableStateFlow<List<MediaSource>>(emptyList())
    val sources: StateFlow<List<MediaSource>> = _sources.asStateFlow()

    private val _source = MutableStateFlow<MediaSource?>(null)
    val source: StateFlow<MediaSource?> = _source.asStateFlow()

    private val _path = MutableStateFlow(BrowsePath())
    val path: StateFlow<BrowsePath> = _path.asStateFlow()

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    private val _status = MutableStateFlow<LibraryStatus>(LibraryStatus.Idle)
    val status: StateFlow<LibraryStatus> = _status.asStateFlow()

    private val _played = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a play or search request was sent, so the page can switch to Now playing. */
    val played: SharedFlow<Unit> = _played.asSharedFlow()

    private val _recent = MutableStateFlow(RecentPlays.decode(prefs.getString(KEY_RECENT, "").orEmpty()))

    /** What the driver played lately, newest first. */
    val recent: StateFlow<List<RecentPlay>> = _recent.asStateFlow()

    private val _tabRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when something asks the Media page to show its Library tab. */
    val tabRequests: SharedFlow<Unit> = _tabRequests.asSharedFlow()

    fun requestLibraryTab() {
        _tabRequests.tryEmit(Unit)
    }

    /** Debug demo mode: one pretend music app with canned playlists, for screenshots. */
    private var demo = false

    fun startDemo() {
        if (!com.aiconsole.kittlauncher2.BuildConfig.DEBUG) return
        demo = true
        _recent.value = DEMO_RECENT
    }

    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private var subscribed: String? = null
    private var generation = 0

    private val icons = HashMap<String, Bitmap>()
    private val covers = object : LruCache<String, Bitmap>(80) {}
    private val http by lazy { OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build() }

    val lastUsed: String? get() = if (demo) DEMO_SOURCE.packageName else prefs.getString(KEY_LAST, null)

    /** Finds the installed apps that offer a library. [nowPlaying] and the last-used app go first. */
    fun refreshSources(nowPlaying: String?) {
        if (demo) {
            _sources.value = listOf(DEMO_SOURCE)
            return
        }
        val pm = app.packageManager
        val intent = Intent(SERVICE_INTERFACE)
        val found = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentServices(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, 0)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not list music apps", e)
            emptyList()
        }
        val list = found
            .mapNotNull { it.serviceInfo }
            .filter { it.exported && it.packageName != app.packageName }
            .distinctBy { it.packageName }
            .map { MediaSource(it.packageName, it.applicationInfo?.loadLabel(pm)?.toString() ?: it.packageName, ComponentName(it.packageName, it.name)) }
        _sources.value = MediaBrowseRules.order(list, { it.packageName }, { it.label }, nowPlaying, lastUsed)
    }

    /**
     * Connects to [source] and shows the top of its library. With [thenSearch] (voice command) or
     * [thenPlayId] (a recent item) it asks the app to play that as soon as it connects, then lets
     * go of the connection.
     */
    fun open(source: MediaSource, thenSearch: String? = null, thenPlayId: String? = null) {
        disconnect()
        if (demo) {
            _source.value = source
            _path.value = BrowsePath().push(Crumb("root", source.label))
            _items.value = DEMO_ITEMS
            _status.value = LibraryStatus.Ready
            return
        }
        prefs.edit().putString(KEY_LAST, source.packageName).apply()
        _source.value = source
        _status.value = LibraryStatus.Connecting
        val gen = ++generation
        val callbacks = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                if (gen != generation) return
                val b = browser ?: return
                controller = runCatching { MediaController(app, b.sessionToken) }.getOrNull()
                val root = b.root
                _path.value = BrowsePath().push(Crumb(root, source.label))
                load(root)
                if (thenSearch != null || thenPlayId != null) {
                    if (thenPlayId != null) playId(thenPlayId) else search(thenSearch.orEmpty())
                    main.postDelayed({ if (gen == generation) disconnect() }, HEADLESS_HOLD_MS)
                }
            }

            override fun onConnectionFailed() = fail(gen, "${source.label} wouldn't share its library. Some apps only allow approved car apps; open the app instead.")

            override fun onConnectionSuspended() = fail(gen, "Lost the connection to ${source.label}.")
        }
        try {
            val b = MediaBrowser(app, source.service, callbacks, null)
            browser = b
            b.connect()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Connect to ${source.packageName} failed", e)
            fail(gen, "${source.label} wouldn't share its library. Some apps only allow approved car apps; open the app instead.")
            return
        }
        main.postDelayed({
            if (gen == generation && _status.value == LibraryStatus.Connecting) {
                fail(gen, "${source.label} didn't answer. Open the app once, then try again.")
            }
        }, CONNECT_TIMEOUT_MS)
    }

    /** Opens a folder (playlist, artist, album…) of the current app. */
    fun openItem(item: LibraryItem) {
        _path.value = _path.value.push(Crumb(item.id, item.title))
        load(item.id)
    }

    fun up() {
        val next = _path.value.pop()
        if (next === _path.value) return
        _path.value = next
        next.current?.let { load(it.id) }
    }

    /** Starts [item] in its own app. */
    fun play(item: LibraryItem) {
        if (playId(item.id)) {
            val s = _source.value ?: return
            remember(RecentPlay(s.packageName, s.label, item.id, null, item.title, item.subtitle, item.iconUri))
        }
    }

    /** Plays something from the Recent list, connecting to its app first. */
    fun playRecent(r: RecentPlay) {
        val source = _sources.value.firstOrNull { it.packageName == r.sourcePackage } ?: run {
            _status.value = LibraryStatus.Failed("${r.sourceLabel} isn't installed any more.")
            return
        }
        remember(r)
        if (r.mediaId != null) open(source, thenPlayId = r.mediaId) else open(source, thenSearch = r.query)
    }

    private fun remember(r: RecentPlay) {
        if (demo) return
        _recent.value = RecentPlays.push(_recent.value, r)
        prefs.edit().putString(KEY_RECENT, RecentPlays.encode(_recent.value)).apply()
    }

    private fun playId(id: String): Boolean {
        val c = controller ?: return false
        ReturnHome.noteAudio()
        try {
            c.transportControls.playFromMediaId(id, null)
            _played.tryEmit(Unit)
            return true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Play $id failed", e)
            _status.value = LibraryStatus.Failed("${_source.value?.label ?: "The app"} wouldn't start that.")
            return false
        }
    }

    /** Asks the app to find and play [query] (a song, artist, album or playlist name). */
    fun search(query: String) {
        val q = MediaBrowseRules.cleanQuery(query) ?: return
        val c = controller ?: return
        ReturnHome.noteAudio()
        try {
            c.transportControls.playFromSearch(q, Bundle().apply { putString(EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*") })
            _played.tryEmit(Unit)
            _source.value?.let { remember(RecentPlay(it.packageName, it.label, null, q, q, "Search", null)) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Search '$q' failed", e)
            _status.value = LibraryStatus.Failed("${_source.value?.label ?: "The app"} couldn't search for that.")
        }
    }

    /** Drops the connection (the page left the library tab). */
    fun disconnect() {
        generation++
        subscribed?.let { id -> runCatching { browser?.unsubscribe(id) } }
        subscribed = null
        runCatching { browser?.disconnect() }
        browser = null
        controller = null
        icons.clear()
        _source.value = null
        _items.value = emptyList()
        _path.value = BrowsePath()
        _status.value = LibraryStatus.Idle
    }

    private fun load(parentId: String) {
        val b = browser ?: return
        val gen = generation
        _status.value = LibraryStatus.Loading
        subscribed?.let { runCatching { b.unsubscribe(it) } }
        subscribed = parentId
        try {
            b.subscribe(parentId, object : MediaBrowser.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                    if (gen != generation || parentId != subscribed) return
                    icons.clear()
                    _items.value = children.mapNotNull(::convert).take(MediaBrowseRules.MAX_ITEMS)
                    _status.value = LibraryStatus.Ready
                }

                override fun onError(parentId: String) = fail(gen, "${_source.value?.label ?: "The app"} couldn't load that list.")
            })
        } catch (e: RuntimeException) {
            Log.w(TAG, "Subscribe $parentId failed", e)
            fail(gen, "${_source.value?.label ?: "The app"} couldn't load that list.")
        }
    }

    private fun convert(item: MediaBrowser.MediaItem): LibraryItem? {
        val d = item.description
        val id = d.mediaId ?: return null
        val title = d.title?.toString()?.ifBlank { null } ?: return null
        d.iconBitmap?.let { icons[id] = it }
        return LibraryItem(id, title, d.subtitle?.toString()?.ifBlank { null }, item.isBrowsable, item.isPlayable, d.iconUri?.toString())
    }

    private fun fail(gen: Int, message: String) {
        if (gen != generation) return
        _items.value = emptyList()
        _status.value = LibraryStatus.Failed(message)
    }

    /** The cover for [item]: the bitmap the app sent, or one fetched from its icon address. */
    suspend fun cover(item: LibraryItem): Bitmap? {
        icons[item.id]?.let { return it }
        val uri = item.iconUri ?: return null
        covers.get(uri)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { fetch(uri) } ?: return null
        covers.put(uri, bitmap)
        return bitmap
    }

    private suspend fun fetch(uri: String): Bitmap? {
        val bytes = try {
            val parsed = Uri.parse(uri)
            when (parsed.scheme) {
                "http", "https" -> http.newCall(Request.Builder().url(uri).build()).execute().use { r ->
                    if (r.isSuccessful && r.body.contentLength() <= MAX_COVER_BYTES) r.body.byteStream().use(::readCapped) else null
                }
                "content", "android.resource" -> app.contentResolver.openInputStream(parsed)?.use(::readCapped)
                else -> null
            }
        } catch (e: java.io.IOException) {
            null
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return null
        currentCoroutineContext().ensureActive()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = MediaBrowseRules.sampleSize(bounds.outWidth, bounds.outHeight, COVER_PX) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** Icon addresses come from other apps, so reads stop at [MAX_COVER_BYTES]; null when the file is bigger. */
    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > MAX_COVER_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        val DEMO_SOURCE = MediaSource("demo.music", "Demo Music", ComponentName("demo.music", "Demo"))
        val DEMO_ITEMS = listOf(
            LibraryItem("d1", "Night Drive", "Playlist · 42 songs", browsable = true, playable = true, iconUri = null),
            LibraryItem("d2", "Road Trip Mix", "Playlist · 87 songs", browsable = true, playable = true, iconUri = null),
            LibraryItem("d3", "Synthwave Essentials", "Playlist · 60 songs", browsable = true, playable = true, iconUri = null),
            LibraryItem("d4", "Kavinsky", "Artist", browsable = true, playable = false, iconUri = null),
            LibraryItem("d5", "Mitch Murder", "Artist", browsable = true, playable = false, iconUri = null),
            LibraryItem("d6", "Nightcall", "Kavinsky · OutRun", browsable = false, playable = true, iconUri = null)
        )
        val DEMO_RECENT = listOf(
            RecentPlay("demo.music", "Demo Music", "d1", null, "Night Drive", "Playlist", null),
            RecentPlay("demo.music", "Demo Music", null, "daft punk", "daft punk", "Search", null),
            RecentPlay("demo.music", "Demo Music", "d6", null, "Nightcall", "Kavinsky", null),
            RecentPlay("demo.music", "Demo Music", "d2", null, "Road Trip Mix", "Playlist", null)
        )
        const val TAG = "MediaLibrary"
        const val PREFS = "media_library"
        const val KEY_LAST = "last_source"
        const val KEY_RECENT = "recent_plays"
        const val SERVICE_INTERFACE = "android.media.browse.MediaBrowserService"
        const val EXTRA_MEDIA_FOCUS = "android.intent.extra.focus"
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val HEADLESS_HOLD_MS = 5_000L
        const val COVER_PX = 160
        const val MAX_COVER_BYTES = 2L * 1024 * 1024
    }
}
