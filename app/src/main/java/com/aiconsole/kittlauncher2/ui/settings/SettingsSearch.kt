package com.aiconsole.kittlauncher2.ui.settings

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import java.util.Locale

/** One place in KITT Config the search box can jump to. */
data class SettingsEntry(val label: String, val anchor: String, val keywords: String)

/**
 * The KITT Config search: every section with the words a driver might type for it. Plain
 * Kotlin (unit-tested); [SettingsListScope] records where each anchor sits in the list.
 */
object SettingsSearch {

    val ENTRIES = listOf(
        SettingsEntry("System check", "check", "permissions problems fix setup default home launcher webview version"),
        SettingsEntry("AI provider and keys", "ai", "ai gemini perplexity api key model provider diagnostics search"),
        SettingsEntry("Personality", "personality", "persona savage classic unfiltered karr swearing rude sarcasm name"),
        SettingsEntry("Voice engine", "voice", "voice offline neural kokoro ryan piper gemini device tts speech speak download pitch"),
        SettingsEntry("Microphone and speech input", "mic", "mic microphone speech input recognizer listen dictation google fix"),
        SettingsEntry("Hey KITT and replies", "wake", "hey kitt wake word speak replies hands free"),
        SettingsEntry("Read messages aloud", "messages", "messages texts sms whatsapp read aloud notifications greeting"),
        SettingsEntry("Screen size", "display", "display screen size scale zoom text big small"),
        SettingsEntry("Performance / lite mode", "performance", "performance lite slow fast animations memory ram"),
        SettingsEntry("Theme and film editions", "theme", "theme terminator matrix robocop back to the future film edition character colour color"),
        SettingsEntry("Accent colour", "accent", "accent colour color tint"),
        SettingsEntry("Night mode and units", "night", "night dim dark sunset metric imperial units miles km"),
        SettingsEntry("Driving layout, shift light, scanner overlay", "drivinglayout", "driving layout big buttons shift light rpm scanner overlay over apps"),
        SettingsEntry("Navigation and map", "navigation", "navigation map directions route maps waze turn by turn notification access"),
        SettingsEntry("Driving extras", "driving", "driving commentary report card grade recap fuel gas speed limit warning sound effects night theme"),
        SettingsEntry("Extras and problem report", "extras", "mini player weekly backup problem report send phone qr crash"),
        SettingsEntry("Home screen", "home", "home screen status strip icons icon pack button sounds screensaver start wake resume audio"),
        SettingsEntry("Steering-wheel buttons", "buttons", "steering wheel buttons keys learn hardware"),
        SettingsEntry("Driver profiles", "profiles", "driver profiles users personal shared keys"),
        SettingsEntry("Backup and restore", "backup", "backup restore export import save setup"),
        SettingsEntry("Updates", "updates", "updates update version install beta release github"),
        SettingsEntry("Quick links", "links", "bluetooth wifi wi-fi display android settings home app")
    )

    /** Entries whose label or keywords contain every word of [query], best (label) matches first. */
    fun match(query: String, entries: List<SettingsEntry> = ENTRIES): List<SettingsEntry> {
        val words = query.lowercase(Locale.US).split(' ', ',').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries
            .filter { e -> val text = (e.label + " " + e.keywords).lowercase(Locale.US); words.all { it in text } }
            .sortedByDescending { e -> words.count { it in e.label.lowercase(Locale.US) } }
    }
}

/**
 * Wraps the KITT Config LazyColumn: counts items as they're declared so [anchor] knows each
 * section's index for the search box to scroll to.
 */
class SettingsListScope(private val list: LazyListScope, private val anchors: MutableMap<String, Int>) {
    private var count = 0

    fun item(key: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
        list.item(key) { content() }
        count++
    }

    /** The next item starts the section named [name]. */
    fun anchor(name: String) {
        anchors[name] = count
    }
}
