package com.aiconsole.knightriderlauncher.assistant

import android.media.AudioManager
import android.view.KeyEvent
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.data.InstalledApps
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import com.aiconsole.knightriderlauncher.obd.DtcDecoder
import com.aiconsole.knightriderlauncher.stream.StreamSites
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Launcher-side actions the home screen carries out (it owns the pages, stream and overlays). */
sealed interface LauncherEvent {
    data class ShowPage(val page: String) : LauncherEvent
    data class OpenStream(val url: String, val label: String) : LauncherEvent
    data object CloseStream : LauncherEvent
    data object OpenDiagnostics : LauncherEvent
}

object LauncherBus {
    private val _events = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    fun send(event: LauncherEvent) {
        _events.tryEmit(event)
    }
}

/** Carries out a [VoiceCommand] and returns what KITT says about it. */
class LauncherCommandRunner(private val app: KittApplication) {

    private val unfiltered get() = app.settings.persona.value == PersonaStyle.Unfiltered

    private fun say(classic: String, rude: String) = if (unfiltered) rude else classic

    suspend fun run(command: VoiceCommand): String = when (command) {
        is VoiceCommand.OpenApp -> openApp(command.query)
        is VoiceCommand.ShowPage -> {
            LauncherBus.send(LauncherEvent.ShowPage(command.page))
            say("${command.page.replaceFirstChar(Char::uppercase)} page.", "Fine. ${command.page.replaceFirstChar(Char::uppercase)}. Happy now?")
        }
        is VoiceCommand.Stream -> stream(command.site)
        VoiceCommand.CloseStream -> {
            LauncherBus.send(LauncherEvent.CloseStream)
            say("Closing the window.", "Window's gone. Eyes on the road, genius.")
        }
        VoiceCommand.OpenDiagnostics -> {
            LauncherBus.send(LauncherEvent.OpenDiagnostics)
            say("Opening diagnostics.", "Diagnostics. Let's see what you've broken.")
        }
        VoiceCommand.Play -> media(KeyEvent.KEYCODE_MEDIA_PLAY, "Playing.", "Music's on. Try not to sing.")
        VoiceCommand.Pause -> media(KeyEvent.KEYCODE_MEDIA_PAUSE, "Paused.", "Paused. Blessed silence.")
        VoiceCommand.NextTrack -> media(KeyEvent.KEYCODE_MEDIA_NEXT, "Next track.", "Skipping. Your taste, not mine.")
        VoiceCommand.PreviousTrack -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous track.", "Going back. Again.")
        is VoiceCommand.Navigate -> {
            LauncherBus.send(LauncherEvent.ShowPage("drive"))
            app.navigator.navigateTo(command.destination)
        }
        VoiceCommand.StopNavigation -> {
            app.navigator.stop()
            say("Navigation ended.", "Directions off. Good luck finding it yourself.")
        }
        VoiceCommand.ReadCodes -> readCodes()
        VoiceCommand.StartLogging -> app.dataLogger.start()?.let { "I can't start logging: $it" }
            ?: say("Data logging started.", "Logging everything. Every bad decision, recorded.")
        VoiceCommand.StopLogging -> {
            app.dataLogger.stop()
            say("Logging stopped. The file is in Diagnostics, Data.", "Logging stopped. The evidence is in Diagnostics, Data.")
        }
    }

    private suspend fun openApp(query: String): String {
        val apps = InstalledApps.load(app, app.settings.iconPack.value)
        val index = VoiceCommands.bestMatch(query, apps.map { it.label })
            ?: return say("I can't find an app called $query.", "There's no app called $query. Try again, slower.")
        val target = apps[index]
        InstalledApps.launch(app, target)
        return say("Opening ${target.label}.", "Opening ${target.label}. Obviously.")
    }

    private fun stream(query: String): String {
        val saved = StreamSites.decode(app.settings.streamBookmarks.value)
        val sites = saved + StreamSites.video + StreamSites.audio
        val index = VoiceCommands.bestMatch(query, sites.map { it.label })
            ?: return say("I don't have $query in the site list. Save it from the window first.", "No idea what $query is. Save it in the window first.")
        val site = sites[index]
        LauncherBus.send(LauncherEvent.OpenStream(site.url, site.label))
        return say("Opening ${site.label} in the window.", "${site.label}, coming up. Don't watch it while driving. Or do, I'm a car.")
    }

    private fun media(keyCode: Int, classic: String, rude: String): String {
        val now = app.media.nowPlaying.value
        when {
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_PLAY -> if (!now.playing) app.media.playPause()
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE -> if (now.playing) app.media.playPause()
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_NEXT -> app.media.next()
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS -> app.media.previous()
            else -> app.getSystemService(AudioManager::class.java)?.let { audio ->
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        }
        return say(classic, rude)
    }

    private suspend fun readCodes(): String = app.obd.readCodes().fold(
        onSuccess = { report ->
            if (report.all.isEmpty()) {
                say("No trouble codes. All systems normal.", "No codes. Shocking, given who's driving.")
            } else {
                val list = report.all.take(4).joinToString("; ") { "$it, ${DtcDecoder.describe(it)}" }
                val more = if (report.all.size > 4) " and ${report.all.size - 4} more" else ""
                say("${report.all.size} codes: $list$more.", "${report.all.size} codes, because of course: $list$more.")
            }
        },
        onFailure = { say("I can't read codes: ${it.message}", "Can't read codes: ${it.message}. Plug me in properly.") }
    )
}
