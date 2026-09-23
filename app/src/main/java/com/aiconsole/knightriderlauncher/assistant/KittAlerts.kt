package com.aiconsole.knightriderlauncher.assistant

import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.settings.PersonaStyle
import com.aiconsole.knightriderlauncher.obd.DtcDecoder
import com.aiconsole.knightriderlauncher.obd.GaugeStore
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.ObdState
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.obd.PidDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class KittAlert(val message: String, val severe: Boolean, val at: Long)

/**
 * KITT's proactive side: greets the driver, announces the vehicle link, and speaks up about
 * overheating, charging problems, new fault codes and user gauge limits. Every alert has a
 * cooldown so KITT informs without nagging.
 */
class KittAlerts(
    private val settings: KittSettings,
    private val obd: ObdManager,
    private val gauges: GaugeStore,
    private val voice: TextToSpeechHelper,
    private val scope: CoroutineScope
) {
    private val _active = MutableStateFlow<KittAlert?>(null)
    val active: StateFlow<KittAlert?> = _active.asStateFlow()

    private val lastRaised = HashMap<String, Long>()
    private var knownCodes: Set<String>? = null
    private var linkAnnounced = false
    private var greeted = false
    private var lowVoltageSince = 0L
    private var clearJob: Job? = null

    init {
        scope.launch(Dispatchers.Main) {
            obd.state.collect { state ->
                if (state is ObdState.Connected && !linkAnnounced) {
                    linkAnnounced = true
                    val codes = obd.dtcs.value?.all.orEmpty()
                    knownCodes = codes.toSet()
                    val status = when (codes.size) {
                        0 -> line("All systems nominal.", "No fault codes. Shocking, considering who's driving.")
                        1 -> line(
                            "I'm reading one fault code, ${codes.first()}.",
                            "One fault code, ${codes.first()}. Congratulations, you broke something."
                        )
                        else -> line(
                            "I'm reading ${codes.size} fault codes.",
                            "${codes.size} fault codes. What the hell have you been doing to me?"
                        )
                    }
                    raise("link", line("Vehicle link established. $status", "Vehicle link's up. $status"), severe = false, cooldownMs = 0)
                } else if (state !is ObdState.Connected) {
                    linkAnnounced = false
                    knownCodes = null
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            obd.dtcs.collect { report ->
                val known = knownCodes ?: return@collect
                val fresh = report?.all.orEmpty().filterNot { it in known }
                knownCodes = known + fresh
                fresh.firstOrNull()?.let { code ->
                    val description = DtcDecoder.describe(code)
                    raise(
                        "dtc_$code",
                        line(
                            "New fault code detected: $code, $description. Ask me about it when convenient.",
                            "Oh, fantastic. New fault code: $code, $description. Ask me about it once you're done wrecking the rest of me."
                        ),
                        severe = true,
                        cooldownMs = 0
                    )
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            obd.readings.collect { checkReadings() }
        }
    }

    /** Called once when the launcher first appears. */
    fun greetOnce() {
        if (greeted || !settings.greeting.value) return
        greeted = true
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        val name = settings.driverName.value.trim()
        val who = if (name.isNotEmpty()) ", $name" else ""
        raise(
            "greeting",
            line("$part$who. KITT online.", "$part$who. KITT's online, unfortunately for both of us. Try not to hit anything."),
            severe = false,
            cooldownMs = 0
        )
    }

    fun dismiss() {
        _active.value = null
    }

    private fun line(classic: String, unfiltered: String): String =
        if (settings.persona.value == PersonaStyle.Unfiltered) unfiltered else classic

    private fun checkReadings() {
        val now = System.currentTimeMillis()
        val readings = obd.readings.value
        val defs = obd.definitions.value
        val metric = settings.metricUnits.value
        fun fresh(id: String) = readings[id]?.takeIf { now - it.timestamp < 5_000 }?.value

        fresh(PidCatalog.COOLANT)?.let { coolant ->
            if (coolant >= OVERHEAT_C) {
                val def = defs[PidCatalog.COOLANT]
                val temperature = def?.let { spoken(it, coolant, metric) } ?: "critical"
                raise(
                    "overheat",
                    line(
                        "Warning. Engine temperature is $temperature. I suggest pulling over when it's safe.",
                        "Warning, genius: engine temperature is $temperature. Pull over somewhere safe right now, before you cook my damn engine."
                    ),
                    severe = true,
                    cooldownMs = 5 * 60_000
                )
            }
        }

        val rpm = fresh(PidCatalog.RPM)
        val volts = fresh(PidCatalog.ADAPTER_VOLTAGE)
        if (rpm != null && volts != null && rpm > 600 && volts < LOW_CHARGING_V) {
            if (lowVoltageSince == 0L) lowVoltageSince = now
            if (now - lowVoltageSince > 20_000) {
                val reading = "%.1f".format(java.util.Locale.US, volts)
                raise(
                    "charging",
                    line(
                        "Charging voltage is only $reading volts with the engine running. The alternator or battery may need attention.",
                        "Charging voltage is a pathetic $reading volts with the engine running. Your alternator or battery is crapping out. Get it checked."
                    ),
                    severe = true,
                    cooldownMs = 10 * 60_000
                )
            }
        } else {
            lowVoltageSince = 0L
        }

        for (gauge in gauges.gauges.value) {
            if (gauge.warnAbove == null && gauge.warnBelow == null) continue
            if (gauge.pidId == PidCatalog.COOLANT && gauge.warnAbove != null && gauge.warnAbove >= OVERHEAT_C) continue
            val def = defs[gauge.pidId] ?: continue
            val value = fresh(gauge.pidId) ?: continue
            if (gauge.isWarning(value)) {
                raise(
                    "gauge_${gauge.id}",
                    line(
                        "${def.name} is ${spoken(def, value, metric)}, outside the limit you set.",
                        "${def.name} is ${spoken(def, value, metric)}, past the limit you set. You asked me to nag, so here I am."
                    ),
                    severe = false,
                    cooldownMs = 3 * 60_000
                )
            }
        }
    }

    private fun raise(key: String, message: String, severe: Boolean, cooldownMs: Long) {
        val now = System.currentTimeMillis()
        val last = lastRaised[key]
        if (last != null && now - last < cooldownMs) return
        lastRaised[key] = now
        _active.value = KittAlert(message, severe, now)
        if (settings.voiceAlerts.value) voice.speak(message, interrupt = severe)
        clearJob?.cancel()
        clearJob = scope.launch(Dispatchers.Main) {
            delay(if (severe) 20_000 else 8_000)
            if (_active.value?.at == now) _active.value = null
        }
    }

    private fun spoken(def: PidDefinition, base: Double, metric: Boolean): String {
        val value = ObdManager.formatValue(def.displayValue(base, metric), def)
        val unit = when (val u = def.unit(metric)) {
            "°C" -> "degrees Celsius"
            "°F" -> "degrees"
            "km/h" -> "kilometers per hour"
            "mph" -> "miles per hour"
            "psi" -> "p s i"
            "kPa" -> "kilopascals"
            "V" -> "volts"
            "rpm" -> "r p m"
            "%" -> "percent"
            else -> u
        }
        return "$value $unit".trim()
    }

    private companion object {
        const val OVERHEAT_C = 110.0
        const val LOW_CHARGING_V = 12.6
    }
}
