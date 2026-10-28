package com.aiconsole.kittlauncher2.obd

import android.util.Log
import com.aiconsole.kittlauncher2.BuildConfig
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.data.trip.TripMetricLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simulated vehicle data for screenshots and demos, debug builds only: the launcher is started
 * with the `kitt.demo` extra (`adb shell am start -n …/.MainActivity --ez kitt.demo true`).
 * The session is labelled "DEMO (simulated)" everywhere the adapter name shows, and nothing is
 * sent to any adapter or vehicle.
 */
object DemoSession {

    const val EXTRA = "kitt.demo"

    fun startIfRequested(app: KittApplication, requested: Boolean) {
        if (!BuildConfig.DEBUG || !requested) return
        Log.i("DemoSession", "Starting simulated OBD session")
        app.mediaLibrary.startDemo()
        val start = System.currentTimeMillis()
        app.obd.startDemo(
            ObdSessionInfo(
                adapterName = "DEMO (simulated)",
                elmVersion = "ELM327 v1.5",
                protocol = "ISO 15765-4 (CAN 11/500)",
                isCan = true,
                vin = null,
                detectedProfile = null,
                supportedPidCount = 42
            ),
            DtcReport(
                stored = listOf("P0456"),
                pending = emptyList(),
                permanent = emptyList(),
                milOn = true,
                readAt = start,
                readiness = Readiness(
                    milOn = true,
                    dtcCount = 1,
                    compressionIgnition = false,
                    monitors = listOf(
                        ReadinessMonitor("Misfire", true),
                        ReadinessMonitor("Fuel system", true),
                        ReadinessMonitor("Components", true),
                        ReadinessMonitor("Catalyst", true),
                        ReadinessMonitor("Evaporative system", false),
                        ReadinessMonitor("Oxygen sensor", true),
                        ReadinessMonitor("Oxygen sensor heater", true),
                        ReadinessMonitor("EGR / VVT", true)
                    )
                )
            )
        ) { frame((System.currentTimeMillis() - start) / 1000.0) }
        app.appScope.launch(Dispatchers.IO) { seedTrip(app) }
    }

    /** One moment of a steady suburban cruise, in metric base units. */
    fun frame(t: Double): Map<String, Double> {
        val speed = 61.0 + 4.0 * sin(t / 7.0)
        return mapOf(
            PidCatalog.SPEED to speed,
            PidCatalog.RPM to speed * 30.0 + 60.0 * sin(t / 3.0),
            PidCatalog.THROTTLE to 17.0 + 3.0 * sin(t / 5.0),
            PidCatalog.ENGINE_LOAD to 33.0 + 6.0 * sin(t / 4.0),
            PidCatalog.COOLANT to 91.0,
            PidCatalog.INTAKE_TEMP to 26.0,
            PidCatalog.FUEL_LEVEL to 64.0,
            PidCatalog.MODULE_VOLTAGE to 14.2,
            PidCatalog.ADAPTER_VOLTAGE to 14.0,
            PidCatalog.MAF to 13.0 + 2.0 * sin(t / 5.0),
            PidCatalog.FUEL_RATE to 4.3 + 0.6 * sin(t / 5.0),
            "01_0E" to 18.0 + 3.0 * sin(t / 6.0),
            "01_44" to 1.0 + 0.02 * sin(t / 4.0),
            "01_0B" to 130.0 + 12.0 * sin(t / 5.0),
            "01_33" to 101.0,
            "01_5C" to 99.0 + 1.0 * sin(t / 9.0),
            // Simulated tire pressures (kPa, about 32 PSI) so the vehicle HUD has something to show.
            "gm_tpms_lf" to 221.0 + 1.5 * sin(t / 11.0),
            "gm_tpms_rf" to 220.0 + 1.5 * sin(t / 13.0),
            "gm_tpms_lr" to 214.0 + 1.5 * sin(t / 17.0),
            "gm_tpms_rr" to 216.0 + 1.5 * sin(t / 19.0)
        )
    }

    /** A 24-minute drive that ended 50 minutes ago, so the trip computer has something to show. */
    private suspend fun seedTrip(app: KittApplication) {
        val dao = app.tripDatabase.tripMetricDao()
        // Once is enough: relaunching the demo shouldn't stack up copies of the same drive.
        if (dao.samplesSince(System.currentTimeMillis() - 3 * 3_600_000L).isNotEmpty()) return
        val end = System.currentTimeMillis() - 50 * 60_000L
        val seconds = 24 * 60
        val logs = (0 until seconds).map { i ->
            // Stop-and-go for the first and last few minutes, a cruise in between.
            val phase = i.toDouble() / seconds
            val cruise = if (phase < 0.15 || phase > 0.9) 35.0 + 30.0 * sin(i / 40.0 * PI).coerceAtLeast(0.0) else 72.0 + 8.0 * sin(i / 90.0)
            val speed = cruise.coerceIn(0.0, 110.0)
            TripMetricLog(
                timestamp = end - (seconds - i) * 1000L,
                rpm = if (speed < 1) 720 else (speed * 28 + 400).roundToInt(),
                speed = speed.roundToInt(),
                coolantTemp = (40 + i / 6).coerceAtMost(91),
                throttlePosition = (8.0 + speed / 6.0).toFloat(),
                fuelRate = (1.0 + speed / 16.0).toFloat()
            )
        }
        dao.insertAll(logs)
    }
}
