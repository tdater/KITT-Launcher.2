package com.aiconsole.knightriderlauncher.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aiconsole.knightriderlauncher.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AiProvider(val label: String) {
    Gemini("Google Gemini"),
    Perplexity("Perplexity")
}

enum class PersonaStyle(val label: String) {
    Unfiltered("Unfiltered KITT"),
    Classic("Classic KITT")
}

/** All user-facing launcher settings. Plain prefs for options, [SecureStore] for API keys. */
class KittSettings(context: Context, private val scope: CoroutineScope) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("kitt_settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    /** A persisted value exposed as a [StateFlow]. */
    inner class Pref<T>(
        private val key: String,
        default: T,
        read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit
    ) {
        private val flow = MutableStateFlow(prefs.read(key, default))
        val state: StateFlow<T> = flow.asStateFlow()
        val value: T get() = flow.value

        fun set(newValue: T) {
            flow.value = newValue
            prefs.edit().apply { write(key, newValue) }.apply()
        }
    }

    private fun boolPref(key: String, default: Boolean) =
        Pref(key, default, { k, d -> getBoolean(k, d) }, { k, v -> putBoolean(k, v) })

    private fun stringPref(key: String, default: String) =
        Pref(key, default, { k, d -> getString(k, d) ?: d }, { k, v -> putString(k, v) })

    // --- AI core ---
    val provider = Pref(
        "ai_provider", AiProvider.Gemini,
        { k, d -> runCatching { AiProvider.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    val persona = Pref(
        "persona", PersonaStyle.Unfiltered,
        { k, d -> runCatching { PersonaStyle.valueOf(getString(k, d.name)!!) }.getOrDefault(d) },
        { k, v -> putString(k, v.name) }
    )
    val chatModel = stringPref("chat_model", DEFAULT_CHAT_MODEL)
    val diagnosticModel = stringPref("diagnostic_model", DEFAULT_DIAGNOSTIC_MODEL)
    val perplexityChatPreset = stringPref("pplx_chat_preset", "fast")
    val perplexityDiagnosticPreset = stringPref("pplx_diag_preset", "low")
    val webSearch = boolPref("web_search", true)
    val driverName = stringPref("driver_name", "")
    val voiceReply = boolPref("voice_reply", true)
    val greeting = boolPref("greeting", true)
    val voiceAlerts = boolPref("voice_alerts", true)
    val kittVoiceName = stringPref("kitt_voice", "")

    // --- Display ---
    val metricUnits = boolPref("metric_units", false)
    val scannerOverlay = boolPref("scanner_overlay", false)

    // --- OBD ---
    val adapterType = stringPref("obd_adapter_type", "")
    val adapterAddress = stringPref("obd_adapter_address", "")
    val adapterName = stringPref("obd_adapter_name", "")
    val obdAutoConnect = boolPref("obd_auto_connect", true)
    val obdProtocol = stringPref("obd_protocol", "0")
    val obdFastPolling = boolPref("obd_fast_polling", true)
    val manufacturerProfile = stringPref("obd_manufacturer", "auto")
    val manufacturerPids = boolPref("obd_manufacturer_pids", true)

    // --- Secrets (decrypted once, then held in memory) ---
    private val _geminiKey = MutableStateFlow(secure.get(GEMINI_KEY).orEmpty())
    private val _perplexityKey = MutableStateFlow(secure.get(PERPLEXITY_KEY).orEmpty())

    /** Key saved in the app, falling back to a developer key from local.properties. */
    val geminiKey: String get() = _geminiKey.value.ifEmpty { BuildConfig.GEMINI_API_KEY }
    val perplexityKey: String get() = _perplexityKey.value
    val geminiKeySaved: StateFlow<String> = _geminiKey.asStateFlow()
    val perplexityKeySaved: StateFlow<String> = _perplexityKey.asStateFlow()

    fun setGeminiKey(key: String) = saveSecret(GEMINI_KEY, key.trim(), _geminiKey)
    fun setPerplexityKey(key: String) = saveSecret(PERPLEXITY_KEY, key.trim(), _perplexityKey)

    private fun saveSecret(name: String, value: String, flow: MutableStateFlow<String>) {
        flow.value = value
        // Keystore crypto can take a few ms: keep it off the main thread.
        scope.launch(Dispatchers.IO) { secure.put(name, value) }
    }

    companion object {
        const val DEFAULT_CHAT_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_DIAGNOSTIC_MODEL = "gemini-3.8-flash"
        private const val GEMINI_KEY = "gemini_api_key"
        private const val PERPLEXITY_KEY = "perplexity_api_key"
    }
}
