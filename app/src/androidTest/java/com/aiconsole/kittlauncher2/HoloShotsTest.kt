package com.aiconsole.kittlauncher2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiconsole.kittlauncher2.assistant.LauncherBus
import com.aiconsole.kittlauncher2.assistant.LauncherEvent
import com.aiconsole.kittlauncher2.data.settings.NightMode
import com.aiconsole.kittlauncher2.obd.DemoSession
import com.aiconsole.kittlauncher2.ui.theme.UiTheme
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Features-page screenshots for the Holo editions: the drive page in each film theme (car and
 * truck), plus gauges, entertainment and widgets. Sets everything through the app's own settings
 * (not by editing the prefs file) so the intro, greeting and what's-new dialog never get in shot.
 * Files go to /data/local/tmp/holo as name.png.
 */
@RunWith(AndroidJUnit4::class)
class HoloShotsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app: KittApplication = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        val automation = instrumentation.uiAutomation
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.forEach { runCatching { automation.grantRuntimePermission(app.packageName, it) } }
        shell("mkdir -p $DIR")
        shell("cmd package set-home-activity ${app.packageName}/${EditionComponents.mainActivity.name}")
        main {
            app.settings.filmIntros.set(false)
            app.settings.greeting.set(false)
            app.settings.notesSeen.set(99)
            app.settings.drivingMode.set(false)
            app.settings.nightMode.set(NightMode.Off)
            app.settings.uiTheme.set(UiTheme.Kitt)
            app.settings.holoVehicle.set("car")
        }
    }

    private fun launch(): ActivityScenario<Activity> =
        ActivityScenario.launch(Intent(app, EditionComponents.mainActivity).putExtra(DemoSession.EXTRA, true))

    @Test
    fun driveThemes() = launch().use {
        pause(12_000)
        // -e only <theme> runs a single theme, so a stalled emulator costs one short run, not the batch.
        val only = InstrumentationRegistry.getArguments().getString("only")
        for ((name, theme) in THEMES.filter { only == null || it.first == only }) {
            for (vehicle in listOf("car", "truck")) {
                main {
                    app.settings.uiTheme.set(theme)
                    app.settings.holoVehicle.set(vehicle)
                }
                show("Drive", 5_000)
                shot("drive-$name-$vehicle")
            }
        }
    }

    @Test
    fun pages() = launch().use {
        pause(12_000)
        main { app.settings.uiTheme.set(UiTheme.Kitt) }
        for (page in listOf("Gauges", "Entertainment", "Widgets")) {
            show(page, 4_000)
            shot(page.lowercase() + "-kitt")
        }
        main { app.settings.uiTheme.set(UiTheme.Terminator) }
        show("Gauges", 4_000)
        shot("gauges-terminator")
    }

    private fun show(page: String, ms: Long) {
        LauncherBus.send(LauncherEvent.ShowPage(page))
        pause(ms)
    }

    private fun shot(name: String) {
        shell("screencap -p $DIR/$name.png")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun pause(ms: Long) = Thread.sleep(ms)

    private companion object {
        const val DIR = "/data/local/tmp/holo"
        val THEMES = listOf(
            "kitt" to UiTheme.Kitt,
            "karr" to UiTheme.Karr,
            "outrun" to UiTheme.Outrun,
            "terminator" to UiTheme.Terminator,
            "robocop" to UiTheme.RoboCop,
            "matrix" to UiTheme.Colorado,
            "bttf" to UiTheme.BackToTheFuture,
        )
    }
}
