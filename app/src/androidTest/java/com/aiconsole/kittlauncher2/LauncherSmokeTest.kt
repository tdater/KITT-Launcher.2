package com.aiconsole.kittlauncher2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiconsole.kittlauncher2.assistant.LauncherBus
import com.aiconsole.kittlauncher2.assistant.LauncherEvent
import com.aiconsole.kittlauncher2.obd.DemoSession
import com.aiconsole.kittlauncher2.ui.theme.UiTheme
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CI smoke test on an emulator: KITT starts in demo mode, visits every home page and opens every
 * full-screen overlay. A crash anywhere kills the test process and fails the build, so a launcher
 * that crashes on start never reaches the head unit.
 */
@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun grantPermissions() {
        // Granted up front so no system dialog covers the launcher.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.forEach { runCatching { automation.grantRuntimePermission(context.packageName, it) } }
    }

    @Test
    fun everyPageAndOverlayOpensWithoutCrashing() {
        val intent = Intent(context, EditionComponents.mainActivity).putExtra(DemoSession.EXTRA, true)
        ActivityScenario.launch<Activity>(intent).use { scenario ->
            settle(6_000)
            alive(scenario)
            visitPages(scenario)
            openOverlays(scenario)
            alive(scenario)
        }
    }

    /**
     * The Bridge cockpit on every page (OBD dash, gauges, media, AI, games and streaming, widgets,
     * apps) and overlay: the Bridge theme itself, then the cockpit over a colour theme, a film HUD
     * theme and The Matrix (which keeps its rain instead of the starfield).
     */
    @Test
    fun bridgeCockpitOpensEveryPageWithoutCrashing() {
        val app = context.applicationContext as KittApplication
        val theme = app.settings.uiTheme.value
        val cockpit = app.settings.bridgeCockpit.value
        val intent = Intent(context, EditionComponents.mainActivity).putExtra(DemoSession.EXTRA, true)
        try {
            ActivityScenario.launch<Activity>(intent).use { scenario ->
                settle(6_000)
                main { app.settings.uiTheme.set(UiTheme.Bridge) }
                settle(1_500)
                alive(scenario)
                visitPages(scenario)
                openOverlays(scenario)
                for (other in listOf(UiTheme.Kitt, UiTheme.Terminator, UiTheme.Colorado)) {
                    main {
                        app.settings.bridgeCockpit.set(true)
                        app.settings.uiTheme.set(other)
                    }
                    settle(1_500)
                    visitPages(scenario)
                }
                alive(scenario)
            }
        } finally {
            main {
                app.settings.uiTheme.set(theme)
                app.settings.bridgeCockpit.set(cockpit)
            }
        }
    }

    private fun visitPages(scenario: ActivityScenario<Activity>) {
        for (page in listOf("Drive", "Gauges", "Media", "Ai", "Entertainment", "Widgets", "Apps", "Drive")) {
            LauncherBus.send(LauncherEvent.ShowPage(page))
            settle(2_500)
            alive(scenario)
        }
    }

    private fun openOverlays(scenario: ActivityScenario<Activity>) {
        for (event in listOf(LauncherEvent.OpenDiagnostics, LauncherEvent.OpenTrips, LauncherEvent.OpenPursuit, LauncherEvent.OpenConfig)) {
            LauncherBus.send(event)
            settle(3_000)
            alive(scenario)
            LauncherBus.send(LauncherEvent.ShowPage("Drive"))
            settle(1_500)
        }
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    // Plain sleeps: the scanner animates forever, so the main thread never goes idle.
    private fun settle(ms: Long) = Thread.sleep(ms)

    private fun alive(scenario: ActivityScenario<Activity>) = scenario.onActivity { assertFalse("KITT closed", it.isFinishing) }
}
