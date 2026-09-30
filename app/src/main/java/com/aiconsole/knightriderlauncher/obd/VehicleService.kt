package com.aiconsole.knightriderlauncher.obd

import com.aiconsole.knightriderlauncher.assistant.assistantHttpClient
import com.aiconsole.knightriderlauncher.assistant.await
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Vehicle-level features built on OBD data: VIN identity (NHTSA vPIC), an odometer estimate,
 * maintenance reminders, fuel range, and battery/charging health.
 */
class VehicleService(
    private val obd: ObdManager,
    private val settings: KittSettings,
    private val history: PidHistory,
    private val scope: CoroutineScope
) {
    private val http by lazy { assistantHttpClient() }

    val identity: StateFlow<VehicleIdentity?> = settings.vehicleIdentity.state
        .map(::decodeIdentity)
        .stateIn(scope, SharingStarted.Eagerly, decodeIdentity(settings.vehicleIdentity.value))

    val maintenance: StateFlow<List<MaintenanceItem>> = settings.maintenanceItems.state
        .map(Maintenance::decode)
        .stateIn(scope, SharingStarted.Eagerly, Maintenance.decode(settings.maintenanceItems.value))

    private var counterKm = settings.distanceCounterKm.value.toDoubleOrNull() ?: 0.0
    private var lastSpeed: PidReading? = null

    /** Starts integrating distance from vehicle speed and keeps the needed PIDs polled. */
    fun start() {
        obd.watch(WATCH_KEY, setOf(PidCatalog.FUEL_LEVEL, ODOMETER))
        scope.launch {
            obd.readings.collect { readings ->
                val speed = readings[PidCatalog.SPEED] ?: return@collect
                val last = lastSpeed
                lastSpeed = speed
                if (last == null || speed.timestamp <= last.timestamp) return@collect
                val dtH = (speed.timestamp - last.timestamp) / 3_600_000.0
                if (dtH > 10.0 / 3600) return@collect // Gap in data: don't guess.
                val before = counterKm
                counterKm += (last.value + speed.value) / 2 * dtH
                if ((counterKm * 5).toInt() != (before * 5).toInt()) settings.distanceCounterKm.set(counterKm.toString())
            }
        }
    }

    /** ECU odometer (01 A6) if the car reports it, else the driver's reading plus distance driven since. */
    fun odometerKm(): Double? {
        obd.readings.value[ODOMETER]?.value?.let { return it }
        val (baseKm, atCounter) = settings.odometerBaseKm.value.split('|').let {
            (it.getOrNull(0)?.toDoubleOrNull() ?: return null) to (it.getOrNull(1)?.toDoubleOrNull() ?: 0.0)
        }
        return baseKm + (counterKm - atCounter)
    }

    fun setOdometer(km: Double) {
        settings.odometerBaseKm.set("$km|$counterKm")
        if (maintenance.value.isEmpty()) saveMaintenance(Maintenance.defaults(km))
    }

    fun saveMaintenance(items: List<MaintenanceItem>) = settings.maintenanceItems.set(Maintenance.encode(items))

    fun markDone(item: MaintenanceItem) {
        val km = odometerKm() ?: return
        saveMaintenance(maintenance.value.map { if (it == item) it.copy(lastDoneKm = km) else it })
    }

    fun reminder(): String? = odometerKm()?.let { Maintenance.reminder(maintenance.value, it, settings.metricUnits.value) }

    fun battery(): BatteryReport {
        val now = System.currentTimeMillis()
        val span = 5 * 60_000L
        val volts = history.points(PidCatalog.ADAPTER_VOLTAGE, now, span).ifEmpty { history.points(PidCatalog.MODULE_VOLTAGE, now, span) }
        return BatteryHealth.analyze(volts, history.points(PidCatalog.RPM, now, span))
    }

    /** Litres per 100 km from recent trips, if known; used for range. */
    fun rangeKm(avgLitersPer100Km: Double?): Double? {
        val level = obd.readings.value[PidCatalog.FUEL_LEVEL]?.value ?: return null
        val tank = settings.tankSize.value.toDoubleOrNull() ?: return null
        val tankLiters = if (settings.metricUnits.value) tank else tank * FuelMath.LITERS_PER_GALLON
        return FuelMath.rangeKm(level, tankLiters, avgLitersPer100Km ?: return null)
    }

    /** Looks the VIN up in NHTSA's free vPIC database and caches the result. */
    suspend fun lookUp(vin: String): Result<VehicleIdentity> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(VinDecoder.nhtsaUrl(vin)).build()
            val body = http.newCall(request).await().use { r ->
                if (!r.isSuccessful) throw IOException("NHTSA returned HTTP ${r.code}")
                r.body.string()
            }
            val row = JSONObject(body).getJSONArray("Results").getJSONObject(0)
            fun field(name: String) = row.optString(name).trim().takeIf { it.isNotEmpty() && it != "Not Applicable" }
            val engine = listOfNotNull(
                field("DisplacementL")?.let { "%.1fL".format(java.util.Locale.US, it.toDouble()) },
                field("EngineCylinders")?.let { "$it-cyl" },
                field("EngineModel")
            ).joinToString(" ").ifEmpty { null }
            val identity = VehicleIdentity(
                vin = vin,
                year = field("ModelYear"),
                make = field("Make"),
                model = field("Model"),
                trim = field("Trim"),
                engine = engine,
                fuel = field("FuelTypePrimary"),
                drive = field("DriveType"),
                body = field("BodyClass")
            )
            settings.vehicleIdentity.set(encodeIdentity(identity))
            Result.success(identity)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: JSONException) {
            Result.failure(IOException("Unexpected NHTSA response", e))
        } catch (e: NumberFormatException) {
            Result.failure(IOException("Unexpected NHTSA response", e))
        }
    }

    private fun encodeIdentity(v: VehicleIdentity): String =
        listOf(v.vin, v.year, v.make, v.model, v.trim, v.engine, v.fuel, v.drive, v.body).joinToString("\n") { it.orEmpty() }

    private fun decodeIdentity(text: String): VehicleIdentity? {
        val f = text.split('\n')
        if (f.size < 9 || f[0].isEmpty()) return null
        fun g(i: Int) = f[i].ifEmpty { null }
        return VehicleIdentity(f[0], g(1), g(2), g(3), g(4), g(5), g(6), g(7), g(8))
    }

    companion object {
        const val ODOMETER = "01_A6"
        private const val WATCH_KEY = "vehicle_service"
    }
}
