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
import com.aiconsole.kittlauncher2.data.settings.NightMode
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
        // KITT is the home screen (no "another launcher took over" alert), and nothing dims it.
        shell("cmd package set-home-activity ${app.packageName}/${EditionComponents.mainActivity.name}")
        main {
            app.settings.filmIntros.set(false)
            app.settings.uiTheme.set(UiTheme.Kitt)
            app.settings.drivingMode.set(false)
            app.settings.nightMode.set(NightMode.Off)
        }
    }

    private fun launch(): ActivityScenario<Activity> =
        ActivityScenario.launch(Intent(app, EditionComponents.mainActivity).putExtra(DemoSession.EXTRA, true))

    // One small group per emulator (page-media.yml runs each on its own): the CI emulator gives
    // out after a few minutes of this, and a group that dies takes only its own files with it.

    @Test
    fun pages() = launch().use {
        pause(10_000)
        for (page in listOf("Drive", "Gauges", "Media", "Ai", "Entertainment", "Apps")) {
            show(page)
            shot(page.lowercase())
        }
        LauncherBus.send(LauncherEvent.OpenLibrary)
        pause(4_000)
        shot("library")
        show("Drive")
        LauncherBus.send(LauncherEvent.ShowStream)
        pause(3_000)
        shot("stream")
        show("Drive")
        overlay(LauncherEvent.OpenAssistant, "kitt")
        overlay(LauncherEvent.OpenDiagnostics, "codes")
        overlay(LauncherEvent.OpenTrips, "trips")
    }

    @Test
    fun config() = launch().use {
        pause(8_000)
        // KITT Config: the top (system check, search), then further down.
        LauncherBus.send(LauncherEvent.OpenConfig)
        pause(3_000)
        shot("config-1")
        repeat(5) { n ->
            scroll()
            shot("config-${n + 2}")
        }
    }

    @Test
    fun themes() = launch().use {
        pause(8_000)
        for (theme in UiTheme.entries) {
            main { app.settings.uiTheme.set(theme) }
            pause(2_400)
            shot("theme-" + theme.name.lowercase())
        }
    }

    @Test
    fun editions() = launch().use {
        pause(8_000)
        // Not Future War: switching to it takes the CI emulator's software GPU down every time (the app
        // itself doesn't crash; the emulator process dies). The page keeps its earlier picture.
        for (edition in FilmEdition.entries - FilmEdition.FutureWar) {
            edition(edition)
            pause(2_400)
            shot("edition-" + edition.name.lowercase())
        }
    }

    @Test
    fun driving() = launch().use {
        // The driving layout switches in after steady driving (demo mode cruises at ~60 km/h).
        main { app.settings.drivingMode.set(true) }
        pause(30_000)
        shot("driving")
    }

    /** The clips: KITT coming online and the page tour. */
    @Test
    fun clips() {
        // KITT comes online: the clip starts before the launcher does.
        val intro = record("kitt")
        launch().use {
            pause(12_000)
            stop(intro)
            val tour = record("tour")
            for (page in listOf("Drive", "Gauges", "Media", "Ai", "Entertainment", "Apps", "Drive")) show(page, 2_000)
            stop(tour)
        }
    }

    /** The clips: themes and film editions. */
    @Test
    fun themeClips() = launch().use {
        pause(8_000)
        val themes = record("themes")
        for (theme in listOf(UiTheme.Kitt, UiTheme.Karr, UiTheme.Outrun, UiTheme.Terminator, UiTheme.RoboCop, UiTheme.Colorado, UiTheme.BackToTheFuture)) {
            main { app.settings.uiTheme.set(theme) }
            pause(2_000)
        }
        stop(themes)
        val editions = record("editions")
        for (edition in listOf(FilmEdition.T1000, FilmEdition.Ed209, FilmEdition.Zion, FilmEdition.Frontier1885)) {
            edition(edition)
            pause(2_500)
        }
        stop(editions)
    }

    private fun edition(edition: FilmEdition) = main {
        app.settings.uiTheme.set(themeFor(edition))
        app.settings.filmChoices.set("${edition.film.name}=${edition.name}:")
    }

    private fun themeFor(edition: FilmEdition): UiTheme = UiTheme.entries.first { it.hud == edition.film }

    private fun show(page: String, ms: Long = 3_000) {
        LauncherBus.send(LauncherEvent.ShowPage(page))
        pause(ms)
    }

    private fun overlay(event: LauncherEvent, name: String) {
        LauncherBus.send(event)
        pause(3_000)
        shot(name)
        show("Drive", 1_500)
    }

    private fun scroll() {
        val size = shell("wm size").substringAfterLast(':').trim().split('x').mapNotNull { it.trim().toIntOrNull() }
        val (w, h) = (size.getOrNull(0) ?: 1024) to (size.getOrNull(1) ?: 600)
        shell("input swipe ${w / 2} ${h * 4 / 5} ${w / 2} ${h / 5} 400")
        pause(1_500)
    }

    private fun shot(name: String) {
        shell("screencap -p $DIR/$prefix$name.png")
    }

    /** Starts screenrecord in the background; [stop] ends it so the file is finished properly. */
    private fun record(name: String): ParcelFileDescriptor =
        instrumentation.uiAutomation.executeShellCommand("screenrecord --bit-rate 3000000 --time-limit 120 $DIR/$prefix$name.mp4")

    private fun stop(recording: ParcelFileDescriptor) {
        shell("pkill -INT screenrecord")
        pause(2_000)
        runCatching { recording.close() }
        pause(3_000)
    }

    /** Runs a shell command and waits for it to finish; returns its output. */
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    // Plain sleeps: the scanner animates forever, so the main thread never goes idle.
    private fun pause(ms: Long) = Thread.sleep(ms)

    private companion object {
        const val DIR = "/data/local/tmp/media"
    }
}
