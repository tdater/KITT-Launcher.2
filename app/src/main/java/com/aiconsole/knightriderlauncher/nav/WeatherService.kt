package com.aiconsole.knightriderlauncher.nav

import android.util.Log
import com.aiconsole.knightriderlauncher.assistant.KittAlerts
import com.aiconsole.knightriderlauncher.assistant.assistantHttpClient
import com.aiconsole.knightriderlauncher.assistant.await
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Current weather at the vehicle from Open-Meteo (free, no key), refreshed every 20 minutes
 * while the launcher is showing. KITT speaks road warnings (ice, freezing rain, fog…).
 */
class WeatherService(
    private val gps: GpsTracker,
    private val alerts: KittAlerts,
    private val settings: KittSettings,
    private val scope: CoroutineScope
) {
    private val http by lazy { assistantHttpClient() }
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                val due = _weather.value?.let { System.currentTimeMillis() - it.at >= REFRESH_MS } ?: true
                if (due) refresh()
                delay(if (_weather.value == null) RETRY_MS else CHECK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun refresh() {
        val (lat, lon) = withContext(Dispatchers.Main) { gps.position() } ?: return
        try {
            val body = withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url(WeatherRules.forecastUrl(lat, lon)).build()).await().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    r.body.string()
                }
            }
            val current = JSONObject(body).getJSONObject("current")
            val weather = Weather(
                tempC = current.getDouble("temperature_2m"),
                code = current.getInt("weather_code"),
                windKmh = current.optDouble("wind_speed_10m").takeUnless { it.isNaN() },
                precipMm = current.optDouble("precipitation").takeUnless { it.isNaN() },
                at = System.currentTimeMillis()
            )
            _weather.value = weather
            if (settings.weatherAlerts.value) WeatherRules.roadWarning(weather)?.let(alerts::weatherWarning)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Weather unavailable: ${e.message}")
        } catch (e: JSONException) {
            Log.w(TAG, "Unexpected weather response", e)
        }
    }

    private companion object {
        const val TAG = "Weather"
        const val REFRESH_MS = 20 * 60_000L
        const val CHECK_MS = 60_000L
        const val RETRY_MS = 30_000L
    }
}
