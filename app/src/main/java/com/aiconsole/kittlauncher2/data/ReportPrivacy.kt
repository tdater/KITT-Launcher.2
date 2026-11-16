package com.aiconsole.kittlauncher2.data

/**
 * Keeps identifying details out of the "Report a problem" file, which can leave the head unit
 * through the share sheet or the Wi-Fi QR server. Settings print from an allow-list (a new key
 * shows up by name only until it's added here), and VINs, Bluetooth addresses and the driver's
 * name are masked anywhere in the report. Plain Kotlin (unit-tested).
 */
object ReportPrivacy {

    /** Settings whose values help with a problem and say nothing about the driver. */
    private val SHOWN = setOf(
        "accent_color", "ai_provider", "auto_backup", "beta_updates", "block_ads", "bridge_cockpit",
        "button_sounds", "chat_model", "dark_pages", "diagnostic_model", "drive_recap",
        "driving_commentary", "film_characters", "film_choices", "film_intros", "fuel_alerts",
        "gamepad_keyboard", "gamepad_mode", "gemini_voice", "holo_vehicle", "icon_pack", "kitt_voice",
        "lite_mode", "local_voice", "local_voice_speaker", "matrix_rain", "metric_units", "mini_player",
        "nav_notifications", "nav_voice", "night_mode", "night_theme", "obd_adapter_name",
        "obd_adapter_type", "obd_auto_connect", "obd_fast_polling", "obd_manufacturer",
        "obd_manufacturer_pids", "obd_protocol", "pplx_chat_preset", "pplx_diag_preset",
        "pplx_local_model", "read_messages", "return_to_launcher", "scanner_overlay",
        "screensaver_minutes", "shift_light", "shift_rpm", "show_map", "side_panel_nav_app",
        "sound_effects", "speech_input", "speech_recognizer", "speed_alert_margin", "speed_alerts",
        "speed_limits", "startup_app", "startup_resume_media", "status_strip", "stream_desktop",
        "tank_size", "themed_icons", "touch_pad", "ui_scale", "ui_theme", "update_repo",
        "voice_alerts", "voice_engine", "voice_recognition_mic", "voice_reply", "wake_offline",
        "wake_word", "weather_alerts", "web_search"
    )

    /** Settings shown masked: the vehicle and adapter matter for OBD problems, their identifiers don't. */
    private val MASKED = setOf("obd_adapter_address", "obd_adapter_list", "vehicle_identity")

    private val VIN = Regex("""\b[A-HJ-NPR-Z0-9]{17}\b""")
    private val BLUETOOTH_ADDRESS = Regex("""\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\b""")

    /** One "key = value" line for the report's settings section. */
    fun settingLine(key: String, value: Any?, driverName: String = ""): String {
        val text = value.toString().replace('\n', ' ')
        return when (key) {
            in SHOWN, in MASKED -> "$key = ${scrub(text, driverName).take(MAX_VALUE)}"
            else -> "$key = (not shown)"
        }
    }

    /** Masks VINs (last 6 kept), Bluetooth addresses (last 2 bytes kept) and the driver's name. */
    fun scrub(text: String, driverName: String = ""): String {
        var out = VIN.replace(text) { match ->
            val vin = match.value
            if (vin.count(Char::isDigit) >= 2 && vin.count(Char::isLetter) >= 2) maskVin(vin) else vin
        }
        out = BLUETOOTH_ADDRESS.replace(out) { maskAddress(it.value) }
        val name = driverName.trim()
        if (name.length >= MIN_NAME) {
            out = Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).replace(out, "[driver]")
        }
        return out
    }

    fun maskVin(vin: String): String = "*".repeat((vin.length - 6).coerceAtLeast(0)) + vin.takeLast(6)

    fun maskAddress(address: String): String {
        val separator = if ('-' in address) "-" else ":"
        val parts = address.split(':', '-')
        return (List(parts.size - 2) { "XX" } + parts.takeLast(2)).joinToString(separator)
    }

    private const val MAX_VALUE = 200
    private const val MIN_NAME = 2
}
