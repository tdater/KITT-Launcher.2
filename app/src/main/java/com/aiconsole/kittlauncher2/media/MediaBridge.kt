package com.aiconsole.kittlauncher2.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.aiconsole.kittlauncher2.nav.NavNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's playing in whichever app holds the active media session. */
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val durationMs: Long,
    /** Position at [positionAtElapsed] (SystemClock.elapsedRealtime), advancing at [speed] while playing. */
    val positionMs: Long,
    val positionAtElapsed: Long,
    val speed: Float
) {
    fun positionNow(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        val pos = if (playing) positionMs + ((nowElapsed - positionAtElapsed) * speed).toLong() else positionMs
        return if (durationMs > 0) pos.coerceIn(0, durationMs) else pos.coerceAtLeast(0)
    }
}

/**
 * Follows the system's active media sessions (Spotify, YouTube Music, radio apps…) and exposes
 * the most relevant one. Reading sessions requires the notification access the launcher already
 * asks for to show turn-by-turn; without it [hasAccess] is false and the dock falls back to
 * plain media keys. All callbacks run on the main thread.
 */
class MediaBridge(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(appContext, NavNotificationListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        track(list.orEmpty())
    }

    /** Starts following sessions; safe to call repeatedly (e.g. after access is granted). */
    fun start() {
        main.post {
            val sessions = activeSessions() ?: return@post
            if (!started) {
                try {
                    manager?.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
                    started = true
                } catch (e: SecurityException) {
                    _hasAccess.value = false
                    return@post
                }
            }
            track(sessions)
        }
    }

    fun playPause() {
        val c = current ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = current?.transportControls?.skipToNext()

    fun previous() = current?.transportControls?.skipToPrevious()

    fun seekTo(positionMs: Long) = current?.transportControls?.seekTo(positionMs)

    /** Opens the app that owns the session. */
    fun openApp() {
        val pkg = current?.packageName ?: return
        appContext.packageManager.getLaunchIntentForPackage(pkg)?.let {
            appContext.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun activeSessions(): List<MediaController>? = try {
        manager?.getActiveSessions(listener).orEmpty().also { _hasAccess.value = true }
    } catch (e: SecurityException) {
        _hasAccess.value = false
        null
    }

    private fun track(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(controllerCallback) } }
        controllers = list
        list.forEach { it.registerCallback(controllerCallback, main) }
        refresh()
    }

    /** Prefers a session that's playing; otherwise the most recent one. */
    private fun refresh() {
        val chosen = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        current = chosen
        _nowPlaying.value = chosen?.let(::snapshot)
    }

    private fun snapshot(c: MediaController): NowPlaying {
        val meta = c.metadata
        val state = c.playbackState
        val art = try {
            meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Artwork unavailable", e)
            null
        }
        return NowPlaying(
            packageName = c.packageName,
            appLabel = labelOf(c.packageName),
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            art = art,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = state?.position ?: 0,
            positionAtElapsed = state?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        )
    }

    private fun labelOf(pkg: String): String = try {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private companion object {
        const val TAG = "MediaBridge"
    }
}
