package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.nav.NavNotificationListener

/**
 * This edition's activity and notification listener. KITT Launcher keeps its original class
 * names, so an update stays the default home screen and keeps notification access.
 */
object EditionComponents {
    val mainActivity: Class<out MainActivity> = com.aiconsole.knightriderlauncher.MainActivity::class.java
    val notificationListener: Class<out NavNotificationListener> = com.aiconsole.knightriderlauncher.nav.NavNotificationListener::class.java
}
