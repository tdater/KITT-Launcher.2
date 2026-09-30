package com.aiconsole.knightriderlauncher.stream

import android.content.Context
import androidx.core.content.edit
import com.aiconsole.knightriderlauncher.assistant.assistantHttpClient
import com.aiconsole.knightriderlauncher.assistant.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** An installed userscript and whether it runs. */
data class InstalledScript(val script: Userscript, val enabled: Boolean)

/** Userscripts for the stream window: one `<id>.user.js` file each, plus a disabled list. */
class UserscriptStore(context: Context) {

    private val directory = File(context.filesDir, "userscripts")
    private val prefs = context.applicationContext.getSharedPreferences("userscripts", Context.MODE_PRIVATE)
    private val http by lazy { assistantHttpClient() }

    private val _scripts = MutableStateFlow(load())
    val scripts: StateFlow<List<InstalledScript>> = _scripts.asStateFlow()

    /** Downloads a `.user.js` for review before installing. Throws [IOException]. */
    suspend fun fetch(url: String): Userscript = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val body = http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }
        if (body.length > MAX_BYTES) throw IOException("Script is too large")
        Userscripts.parse(body) ?: throw IOException("Not a userscript (no ==UserScript== header with a @name)")
    }

    suspend fun install(script: Userscript) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        File(directory, "${script.id}.user.js").writeText(script.source)
        setEnabledInternal(script.id, true)
        _scripts.value = load()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        File(directory, "$id.user.js").delete()
        setEnabledInternal(id, true)
        _scripts.value = load()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        setEnabledInternal(id, enabled)
        _scripts.value = load()
    }

    private fun setEnabledInternal(id: String, enabled: Boolean) {
        val disabled = prefs.getStringSet(DISABLED, emptySet()).orEmpty().toMutableSet()
        if (enabled) disabled -= id else disabled += id
        prefs.edit { putStringSet(DISABLED, disabled) }
    }

    private fun load(): List<InstalledScript> {
        val disabled = prefs.getStringSet(DISABLED, emptySet()).orEmpty()
        return directory.listFiles { f -> f.name.endsWith(".user.js") }.orEmpty()
            .mapNotNull { f -> runCatching { Userscripts.parse(f.readText()) }.getOrNull() }
            .sortedBy { it.name.lowercase() }
            .map { InstalledScript(it, it.id !in disabled) }
    }

    private companion object {
        const val DISABLED = "disabled"
        const val MAX_BYTES = 2_000_000
    }
}
