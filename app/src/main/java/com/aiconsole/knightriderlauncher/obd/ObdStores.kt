package com.aiconsole.knightriderlauncher.obd

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

enum class GaugeStyle(val label: String) {
    DIAL("Dial"),
    BAR("LED bar"),
    DIGITAL("Digital"),
    GRAPH("Graph")
}

/** One gauge on the dashboard. Warning limits are in the PID's metric base unit. */
data class GaugeConfig(
    val id: String,
    val pidId: String,
    val style: GaugeStyle,
    val warnAbove: Double? = null,
    val warnBelow: Double? = null
) {
    fun isWarning(value: Double): Boolean =
        (warnAbove != null && value > warnAbove) || (warnBelow != null && value < warnBelow)
}

/** User's gauge dashboard, persisted as JSON. */
class GaugeStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("kitt_gauges", Context.MODE_PRIVATE)
    private val _gauges = MutableStateFlow(load())
    val gauges: StateFlow<List<GaugeConfig>> = _gauges.asStateFlow()

    fun add(pidId: String, style: GaugeStyle, warnAbove: Double? = null, warnBelow: Double? = null) =
        save(_gauges.value + GaugeConfig(UUID.randomUUID().toString(), pidId, style, warnAbove, warnBelow))

    fun update(config: GaugeConfig) = save(_gauges.value.map { if (it.id == config.id) config else it })

    fun remove(id: String) = save(_gauges.value.filterNot { it.id == id })

    fun move(id: String, delta: Int) {
        val list = _gauges.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        save(list)
    }

    private fun save(list: List<GaugeConfig>) {
        _gauges.update { list }
        val json = JSONArray()
        list.forEach {
            json.put(
                JSONObject()
                    .put("id", it.id)
                    .put("pid", it.pidId)
                    .put("style", it.style.name)
                    .putOpt("warnAbove", it.warnAbove)
                    .putOpt("warnBelow", it.warnBelow)
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<GaugeConfig> {
        val stored = prefs.getString(KEY, null) ?: return DEFAULTS
        return try {
            val array = JSONArray(stored)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                GaugeConfig(
                    id = o.getString("id"),
                    pidId = o.getString("pid"),
                    style = runCatching { GaugeStyle.valueOf(o.getString("style")) }.getOrDefault(GaugeStyle.BAR),
                    warnAbove = if (o.has("warnAbove")) o.getDouble("warnAbove") else null,
                    warnBelow = if (o.has("warnBelow")) o.getDouble("warnBelow") else null
                )
            }
        } catch (_: JSONException) {
            DEFAULTS
        }
    }

    private companion object {
        const val KEY = "gauges"
        val DEFAULTS = listOf(
            GaugeConfig("d_speed", PidCatalog.SPEED, GaugeStyle.DIGITAL),
            GaugeConfig("d_rpm", PidCatalog.RPM, GaugeStyle.DIAL, warnAbove = 6000.0),
            GaugeConfig("d_coolant", PidCatalog.COOLANT, GaugeStyle.BAR, warnAbove = 108.0),
            GaugeConfig("d_volts", PidCatalog.ADAPTER_VOLTAGE, GaugeStyle.DIGITAL, warnBelow = 11.8),
            GaugeConfig("d_load", PidCatalog.ENGINE_LOAD, GaugeStyle.GRAPH),
            GaugeConfig("d_throttle", PidCatalog.THROTTLE, GaugeStyle.BAR),
            GaugeConfig("d_iat", PidCatalog.INTAKE_TEMP, GaugeStyle.BAR),
            GaugeConfig("d_fuel", PidCatalog.FUEL_LEVEL, GaugeStyle.BAR, warnBelow = 12.0)
        )
    }
}

/** User-defined PIDs (any mode, any header), persisted as JSON. */
class CustomPidStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("kitt_custom_pids", Context.MODE_PRIVATE)
    private val _pids = MutableStateFlow(load())
    val pids: StateFlow<List<PidDefinition>> = _pids.asStateFlow()

    fun upsert(pid: PidDefinition) {
        val list = _pids.value
        save(if (list.any { it.id == pid.id }) list.map { if (it.id == pid.id) pid else it } else list + pid)
    }

    fun remove(id: String) = save(_pids.value.filterNot { it.id == id })

    private fun save(list: List<PidDefinition>) {
        _pids.value = list
        val json = JSONArray()
        list.forEach {
            json.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("short", it.shortName)
                    .put("command", it.command)
                    .put("formula", it.formula)
                    .put("unit", it.customUnit)
                    .put("min", it.min)
                    .put("max", it.max)
                    .putOpt("header", it.header)
                    .put("fast", it.fast)
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun load(): List<PidDefinition> {
        val stored = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(stored)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val formula = o.getString("formula")
                custom(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    shortName = o.optString("short").ifEmpty { o.getString("name") },
                    command = o.getString("command"),
                    formula = formula,
                    unit = o.optString("unit"),
                    min = o.optDouble("min", 0.0),
                    max = o.optDouble("max", 100.0),
                    header = o.optString("header").ifEmpty { null },
                    fast = o.optBoolean("fast", false)
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    companion object {
        private const val KEY = "custom_pids"

        fun custom(
            id: String = "custom_" + UUID.randomUUID().toString().take(8),
            name: String,
            shortName: String,
            command: String,
            formula: String,
            unit: String,
            min: Double,
            max: Double,
            header: String?,
            fast: Boolean
        ) = PidDefinition(
            id = id,
            name = name,
            shortName = shortName,
            command = command.uppercase().filter { it.isLetterOrDigit() },
            formula = formula,
            quantity = Quantity.CUSTOM,
            min = min,
            max = max,
            bytes = FormulaEvaluator.bytesUsed(formula),
            header = header?.uppercase()?.filter { it.isLetterOrDigit() }?.ifEmpty { null },
            source = PidSource.CUSTOM,
            customUnit = unit,
            fast = fast
        )
    }
}
