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
import com.aiconsole.kittlauncher2.obd.DemoSession
import com.aiconsole.kittlauncher2.ui.theme.FilmEdition
import com.aiconsole.kittlauncher2.ui.theme.UiTheme
import com.aiconsole.kittlauncher2.ui.theme.hud
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshots and clips for the features page (run by .github/workflows/page-media.yml, not by
 * the smoke test): KITT in demo mode with simulated vehicle data, every page, the overlays, the
 * themes and film editions. Files go to /data/local/tmp/media as name.png / name.mp4; the
 * workflow converts them to the page's webp and mp4 files. Names get "l2-" on KITT Launcher.2.
 */
@RunWith(AndroidJUnit4::class)
class MediaCaptureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app: KittApplication = ApplicationProvider.getApplicationContext()
    private val prefix = if (BuildConfig.EDITION_NAME.endsWith(".2")) "l2-" else ""

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
        main {
            app.settings.filmIntros.set(false)
            app.settings.uiTheme.set(UiTheme.Kitt)
            app.settings.drivingMode.set(false)
        }
    }

    @Test
    fun capture() {
        // KITT comes online: the clip starts before the launcher does.
        val intro = record("kitt")
        val intent = Intent(app, EditionComponents.mainActivity).putExtra(DemoSession.EXTRA, true)
        ActivityScenario.launch<Activity>(intent).use {
            wait(14_000)
            stop(intro)

            for (page in listOf("Drive", "Gauges", "Media", "Ai", "Games", "Apps")) {
                show(page)
                shot(page.lowercase())
            }
            LauncherBus.send(LauncherEvent.ShowStream)
            wait(3_000)
            shot("stream")
            show("Drive")

            overlay(LauncherEvent.OpenAssistant, "kitt")
            overlay(LauncherEvent.OpenDiagnostics, "codes")
            overlay(LauncherEvent.OpenTrips, "trips")
            // KITT Config: the top (system check, search), then further down.
            LauncherBus.send(LauncherEvent.OpenConfig)
            wait(3_000)
            shot("config-1")
            repeat(5) { n ->
                scroll()
                shot("config-${n + 2}")
            }
            show("Drive")

            // Around the pages.
            val tour = record("tour")
            for (page in listOf("Drive", "Gauges", "Media", "Ai", "Games", "Apps", "Drive")) show(page, 2_200)
            stop(tour)

            // Themes, then the film editions.
            val themes = record("themes")
            for (theme in UiTheme.entries) {
                main { app.settings.uiTheme.set(theme) }
                wait(2_400)
                shot("theme-" + theme.name.lowercase())
            }
            stop(themes)
            val editions = record("editions")
            for (edition in FilmEdition.entries) {
                main {
                    app.settings.uiTheme.set(themeFor(edition))
                    app.settings.filmChoices.set("${edition.film.name}=${edition.name}:")
                }
                wait(2_400)
                shot("edition-" + edition.name.lowercase())
            }
            stop(editions)
            main {
                app.settings.uiTheme.set(UiTheme.Kitt)
                app.settings.filmChoices.set("")
            }

            // The driving layout switches in after steady driving (demo mode cruises at ~60 km/h).
            main { app.settings.drivingMode.set(true) }
            show("Drive", 20_000)
            shot("driving")
            main { app.settings.drivingMode.set(false) }
        }
    }

    private fun themeFor(edition: FilmEdition): UiTheme = UiTheme.entries.first { it.hud == edition.film }

    private fun show(page: String, ms: Long = 3_000) {
        LauncherBus.send(LauncherEvent.ShowPage(page))
        wait(ms)
    }

    private fun overlay(event: LauncherEvent, name: String) {
        LauncherBus.send(event)
        wait(3_000)
        shot(name)
        show("Drive", 1_500)
    }

    private fun scroll() {
        val size = shell("wm size").substringAfterLast(':').trim().split('x').mapNotNull { it.trim().toIntOrNull() }
        val (w, h) = (size.getOrNull(0) ?: 1024) to (size.getOrNull(1) ?: 600)
        shell("input swipe ${w / 2} ${h * 4 / 5} ${w / 2} ${h / 5} 400")
        wait(1_500)
    }

    private fun shot(name: String) {
        shell("screencap -p $DIR/$prefix$name.png")
    }

    /** Starts screenrecord in the background; [stop] ends it so the file is finished properly. */
    private fun record(name: String): ParcelFileDescriptor =
        instrumentation.uiAutomation.executeShellCommand("screenrecord --bit-rate 6000000 --time-limit 120 $DIR/$prefix$name.mp4")

    private fun stop(recording: ParcelFileDescriptor) {
        shell("pkill -INT screenrecord")
        wait(2_000)
        runCatching { recording.close() }
    }

    /** Runs a shell command and waits for it to finish; returns its output. */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    // Plain sleeps: the scanner animates forever, so the main thread never goes idle.
    private fun wait(ms: Long) = Thread.sleep(ms)

    private companion object {
        const val DIR = "/data/local/tmp/media"
    }
}
