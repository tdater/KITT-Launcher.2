package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.nav.NavNotificationListener

/** This edition's activity and notification listener (KITT Launcher.2: the shared classes). */
object EditionComponents {
    val mainActivity: Class<out MainActivity> = MainActivity::class.java
    val notificationListener: Class<out NavNotificationListener> = NavNotificationListener::class.java
}
