package com.aiconsole.knightriderlauncher.nav

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One turn-by-turn step as the navigation app shows it in its notification. */
data class NavInstruction(
    val app: String,
    /** Usually distance + maneuver, e.g. "0.3 mi · Turn right". */
    val primary: String,
    /** Usually the road, e.g. "onto Main St". */
    val secondary: String?,
    /** ETA / arrival line when the app provides one. */
    val eta: String?,
    /** The app's maneuver arrow, if it attaches one. */
    val maneuver: Bitmap?
)

/** Latest instruction from Google Maps or Waze, shared with the home screen. */
object NavBridge {
    private val _current = MutableStateFlow<NavInstruction?>(null)
    val current: StateFlow<NavInstruction?> = _current.asStateFlow()

    internal fun post(instruction: NavInstruction?) {
        _current.value = instruction
    }

    fun hasAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun accessSettingsIntent(context: Context): android.content.Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, NavNotificationListener::class.java).flattenToString()
            )
        } else {
            android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
}

/**
 * Reads the ongoing navigation notification from Google Maps or Waze so the launcher can show
 * the next turn over its own map. Only those apps' ongoing notifications are looked at;
 * everything else is ignored and nothing is stored.
 */
class NavNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()
            ?.lastOrNull { isNavigation(it) }
            ?.let { NavBridge.post(parse(it)) }
    }

    override fun onListenerDisconnected() {
        NavBridge.post(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isNavigation(sbn)) NavBridge.post(parse(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in NAV_APPS && NavBridge.current.value?.app == NAV_APPS[sbn.packageName]) NavBridge.post(null)
    }

    private fun isNavigation(sbn: StatusBarNotification): Boolean =
        sbn.packageName in NAV_APPS && sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0

    private fun parse(sbn: StatusBarNotification): NavInstruction? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return null
        val icon = runCatching {
            sbn.notification.getLargeIcon()?.loadDrawable(this)?.toBitmap(96, 96)
        }.getOrNull()
        return NavInstruction(
            app = NAV_APPS.getValue(sbn.packageName),
            primary = title ?: text.orEmpty(),
            secondary = if (title != null) text?.takeIf { it.isNotEmpty() } else null,
            eta = sub?.takeIf { it.isNotEmpty() },
            maneuver = icon
        )
    }

    private companion object {
        val NAV_APPS = mapOf(
            "com.google.android.apps.maps" to "Google Maps",
            "com.google.android.apps.mapslite" to "Google Maps Go",
            "com.waze" to "Waze"
        )
    }
}
