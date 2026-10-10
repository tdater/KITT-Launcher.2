package com.aiconsole.kittlauncher2.nav

/** Current conditions at the vehicle. Metric base units. */
data class Weather(val tempC: Double, val code: Int, val windKmh: Double?, val precipMm: Double?, val at: Long)

/** Plain-Kotlin WMO weather-code wording and road warnings (Open-Meteo uses WMO codes). */
object WeatherRules {

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81 -> "Showers"
        82 -> "Violent showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> "Unknown"
    }

    /** A road warning worth speaking, or null. Checked most serious first. */
    fun roadWarning(w: Weather): String? = when {
        w.code in setOf(56, 57, 66, 67) -> "Freezing rain reported. Roads and bridges may be icy."
        w.code in setOf(96, 99) -> "Thunderstorm with hail nearby."
        w.code == 75 || w.code == 86 -> "Heavy snow. Expect poor traction and visibility."
        w.tempC <= 1.0 && ((w.precipMm ?: 0.0) > 0.0 || w.code in 51..86) -> "Near freezing with precipitation. Watch for black ice."
        w.tempC <= 1.0 -> "It's near freezing. Bridges and shaded roads can ice first."
        w.code == 45 || w.code == 48 -> "Fog. Reduced visibility."
        w.code == 65 || w.code == 82 -> "Heavy rain. Watch for standing water."
        w.code == 95 -> "Thunderstorm in the area."
        (w.windKmh ?: 0.0) >= 60 -> "Strong winds. Careful with high-sided vehicles."
        else -> null
    }

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m,precipitation"
            .format(java.util.Locale.US, lat, lon)
}
