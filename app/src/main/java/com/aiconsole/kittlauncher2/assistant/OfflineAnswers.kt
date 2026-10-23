package com.aiconsole.kittlauncher2.assistant

import com.aiconsole.kittlauncher2.obd.BatteryHealth
import com.aiconsole.kittlauncher2.obd.PidCatalog
import java.util.Locale
import kotlin.math.roundToInt

/** What KITT knows about the car right now, for [OfflineAnswers]. Readings in metric base units. */
data class OfflineFacts(
    val connected: Boolean,
    /** Fresh readings by PidCatalog id (°C, km/h, %, V, rpm). */
    val readings: Map<String, Double>,
    val metric: Boolean,
    val storedCodes: List<String> = emptyList(),
    val pendingCodes: List<String> = emptyList(),
    /** Battery / charging warnings from the voltage history. */
    val batteryWarnings: List<String> = emptyList(),
    val maintenance: String? = null,
    val rangeKm: Double? = null,
    /** Plain-English meaning of a code (DtcDecoder.describe). */
    val describe: (String) -> String = { "" }
)

/**
 * KITT's answers with no signal and no AI: the common questions about the car (temperatures,
 * battery, fuel, speed, codes, "is everything OK?") answered straight from the live data, so they
 * are instant and always right. Safety problems (overheating, not charging) always come first.
 * Anything else returns null and KITT says it needs a signal. Plain Kotlin (unit-tested).
 */
object OfflineAnswers {

    const val OVERHEAT_C = 110.0
    const val HOT_C = 105.0
    const val COLD_C = 60.0
    const val LOW_FUEL_PCT = 12.0

    private val CODE = Regex("\\b([PCBU][0-3][0-9A-F]{3})\\b", RegexOption.IGNORE_CASE)

    // Whole words (plus -s, -ing, -ed): "gas" mustn't match "gasket".
    private fun has(q: String, vararg words: String) = words.any { Regex("\\b$it(s|ing|ed)?\\b").containsMatchIn(q) }

    fun answer(question: String, f: OfflineFacts): String? {
        val q = question.lowercase(Locale.US)
        CODE.find(question)?.let { m -> return code(m.value.uppercase(Locale.US), f) }
        return when {
            has(q, "code", "check engine", "fault", "warning light", "dtc", "engine light") -> codes(f)
            has(q, "coolant", "overheat", "engine temp", "temp", "temperature", "how hot", "running hot") -> coolant(f)
            has(q, "battery", "volt", "voltage", "charge", "charging", "alternator") -> battery(f)
            has(q, "fuel", "gas", "petrol", "range", "tank", "empty") -> fuel(f)
            has(q, "service", "maintenance", "oil change") -> f.maintenance?.let { "$it." } ?: "No service is due that I know of."
            has(q, "speed", "how fast") -> reading(f, PidCatalog.SPEED) { "You're doing ${speed(it, f.metric)}." }
            has(q, "rpm", "revs", "engine speed") -> reading(f, PidCatalog.RPM) { "The engine is at ${it.roundToInt()} rpm." }
            has(q, "intake", "air temp") -> reading(f, PidCatalog.INTAKE_TEMP) { "Intake air is ${temp(it, f.metric)}." }
            has(q, "load") -> reading(f, PidCatalog.ENGINE_LOAD) { "Engine load is ${it.roundToInt()} percent." }
            has(q, "throttle") -> reading(f, PidCatalog.THROTTLE) { "Throttle is at ${it.roundToInt()} percent." }
            has(q, "ok", "okay", "alright", "everything", "status", "health", "problem", "wrong", "how's the car", "how is the car", "condition") ->
                summary(f)
            else -> null
        }
    }

    /** Safety problems in the live data, worst first; empty when there are none. */
    fun warnings(f: OfflineFacts): List<String> = buildList {
        f.readings[PidCatalog.COOLANT]?.let { if (it >= OVERHEAT_C) add("the engine is overheating at ${temp(it, f.metric)}: pull over somewhere safe and switch off") }
        charging(f)?.let(::add)
        f.readings[PidCatalog.FUEL_LEVEL]?.let { if (it <= LOW_FUEL_PCT) add("fuel is low at ${it.roundToInt()} percent") }
        if (f.storedCodes.isNotEmpty()) add("${plural(f.storedCodes.size, "fault code")} stored: ${f.storedCodes.joinToString(", ")}")
    }

    private fun summary(f: OfflineFacts): String {
        if (!f.connected) return NOT_CONNECTED
        val problems = warnings(f)
        if (problems.isNotEmpty()) return "Not quite: " + problems.joinToString("; ") + "."
        val parts = listOfNotNull(
            f.readings[PidCatalog.COOLANT]?.let { "coolant ${temp(it, f.metric)}" },
            volts(f)?.let { "battery %.1f volts".format(Locale.US, it) },
            f.readings[PidCatalog.FUEL_LEVEL]?.let { "fuel ${it.roundToInt()} percent" }
        )
        return "Everything looks normal" + (if (parts.isEmpty()) "" else ": " + parts.joinToString(", ")) + ", and no fault codes."
    }

    private fun code(code: String, f: OfflineFacts): String {
        val meaning = f.describe(code).ifBlank { "a code I don't have a description for" }
        val status = when (code) {
            in f.storedCodes -> " It's stored in the car right now."
            in f.pendingCodes -> " It's pending in the car right now (seen once, not confirmed yet)."
            else -> ""
        }
        return "$code means $meaning.$status"
    }

    private fun codes(f: OfflineFacts): String {
        if (!f.connected) return NOT_CONNECTED
        if (f.storedCodes.isEmpty() && f.pendingCodes.isEmpty()) return "No fault codes are stored."
        return buildString {
            if (f.storedCodes.isNotEmpty()) {
                append("${plural(f.storedCodes.size, "stored code")}: ")
                append(f.storedCodes.joinToString("; ") { "$it, ${f.describe(it)}" }).append(". ")
            }
            if (f.pendingCodes.isNotEmpty()) {
                append("Pending: ").append(f.pendingCodes.joinToString("; ") { "$it, ${f.describe(it)}" }).append(".")
            }
        }.trim()
    }

    private fun coolant(f: OfflineFacts): String = reading(f, PidCatalog.COOLANT) { c ->
        val t = temp(c, f.metric)
        when {
            c >= OVERHEAT_C -> "Coolant is $t. The engine is overheating: pull over somewhere safe and switch off."
            c >= HOT_C -> "Coolant is $t, running hot. Keep an eye on it and ease off."
            c < COLD_C -> "Coolant is $t; the engine is still warming up."
            else -> "Coolant is $t, which is normal."
        }
    }

    private fun battery(f: OfflineFacts): String {
        if (!f.connected) return NOT_CONNECTED
        val v = volts(f) ?: return "I can't read the battery voltage on this car."
        val state = charging(f)?.let { "$it." } ?: if (running(f)) "That's normal; it's charging." else "That's fine with the engine off."
        val extra = f.batteryWarnings.firstOrNull()?.let { " ${it.trimEnd('.')}." }.orEmpty()
        return "Battery is %.1f volts. %s%s".format(Locale.US, v, state, extra)
    }

    private fun fuel(f: OfflineFacts): String = reading(f, PidCatalog.FUEL_LEVEL) { pct ->
        val range = f.rangeKm?.let { " About ${distance(it, f.metric)} to go." }.orEmpty()
        val low = if (pct <= LOW_FUEL_PCT) " That's low; time to fill up." else ""
        "Fuel is at ${pct.roundToInt()} percent.$range$low"
    }

    /** A charging problem worth saying, or null. */
    private fun charging(f: OfflineFacts): String? {
        val v = volts(f) ?: return null
        return when {
            running(f) && v < BatteryHealth.NOT_CHARGING_V -> "the battery isn't charging (%.1f volts with the engine running)".format(Locale.US, v)
            running(f) && v > 15.2 -> "the system is overcharging (%.1f volts)".format(Locale.US, v)
            !running(f) && v < 11.9 -> "the battery is low (%.1f volts)".format(Locale.US, v)
            else -> null
        }
    }

    private fun volts(f: OfflineFacts) = f.readings[PidCatalog.MODULE_VOLTAGE] ?: f.readings[PidCatalog.ADAPTER_VOLTAGE]

    private fun running(f: OfflineFacts) = (f.readings[PidCatalog.RPM] ?: 0.0) > 400

    private fun reading(f: OfflineFacts, id: String, say: (Double) -> String): String {
        if (!f.connected) return NOT_CONNECTED
        return f.readings[id]?.let(say) ?: "This car doesn't report that, or I can't read it right now."
    }

    private fun temp(c: Double, metric: Boolean) =
        if (metric) "${c.roundToInt()} degrees Celsius" else "${(c * 9 / 5 + 32).roundToInt()} degrees Fahrenheit"

    private fun speed(kmh: Double, metric: Boolean) =
        if (metric) "${kmh.roundToInt()} kilometers per hour" else "${(kmh * 0.621371).roundToInt()} miles per hour"

    private fun distance(km: Double, metric: Boolean) =
        if (metric) "${km.roundToInt()} kilometers" else "${(km * 0.621371).roundToInt()} miles"

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    const val NOT_CONNECTED = "I'm not connected to the car right now, so I can't read that."

    /** When nothing matches and there's no signal. */
    const val NEEDS_SIGNAL =
        "I need a signal for that one. Offline I can tell you about the engine temperature, battery, fuel, speed, " +
            "fault codes, or whether everything's OK."
}
