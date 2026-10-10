package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.VehicleContext
import com.aiconsole.kittlauncher2.assistant.VehicleContextFormatter
import com.aiconsole.kittlauncher2.data.trip.MetricBaseline
import com.aiconsole.kittlauncher2.data.trip.TripMetricLog
import com.aiconsole.kittlauncher2.data.trip.VehicleTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleContextFormatterTest {

    private val baseline = MetricBaseline(
        sampleCount = 1_000,
        avgRpm = 1_800.0,
        avgSpeed = 60.0,
        avgCoolant = 90.0,
        maxCoolant = 98,
        avgThrottle = 18.0
    )

    private fun live(coolant: Int, faults: List<String> = emptyList()) = VehicleTelemetry(
        TripMetricLog(timestamp = 0, rpm = 2_000, speed = 100, coolantTemp = coolant, throttlePosition = 20f),
        faults
    )

    @Test
    fun context_notConnected() {
        val text = VehicleContextFormatter.vehicleContext(null, "No trip history logged yet.")
        assertTrue(text.contains("OBD LINK: not connected"))
        assertTrue(text.contains("- none available"))
        assertTrue(text.contains("FAULT CODES: none reported"))
    }

    @Test
    fun context_listsReadingsAndCodes() {
        val ctx = VehicleContext(
            linkStatus = "connected (ISO 15765-4 CAN)",
            vehicle = "Ford / Lincoln, VIN 1FTFW1ET5DFC10312",
            liveReadings = listOf("Engine RPM: 812 rpm", "Vehicle speed: 0 mph"),
            storedCodes = listOf("P0301"),
            pendingCodes = listOf("P0171")
        )
        val text = VehicleContextFormatter.vehicleContext(ctx, "Samples logged: 5")
        assertTrue(text.contains("- Engine RPM: 812 rpm"))
        assertTrue(text.contains("FAULT CODES: stored P0301; pending P0171"))
        assertTrue(text.endsWith("Samples logged: 5"))
    }

    @Test
    fun anomalies_noneWithinNormalRange() {
        assertTrue(VehicleContextFormatter.anomalies(baseline, live(95)).isEmpty())
    }

    @Test
    fun anomalies_flagsCoolantAboveBaseline() {
        val found = VehicleContextFormatter.anomalies(baseline, live(104))
        assertEquals(1, found.size)
        assertTrue(found[0].contains("14°C above"))
    }

    @Test
    fun anomalies_flagsOverheatAndFaults() {
        val found = VehicleContextFormatter.anomalies(baseline, live(112, listOf("P0217")))
        assertTrue(found.any { it.contains("overheating") })
        assertTrue(found.any { it.contains("P0217") })
    }

    @Test
    fun baseline_emptyHistory() {
        val empty = MetricBaseline(0, null, null, null, null, null)
        assertEquals("No trip history logged yet.", VehicleContextFormatter.baseline(empty, null))
    }

    @Test
    fun invalidFramesRejected() {
        assertFalse(TripMetricLog(timestamp = 0, rpm = -1, speed = 0, coolantTemp = 0, throttlePosition = 0f).isValid())
        assertFalse(TripMetricLog(timestamp = 0, rpm = 800, speed = 0, coolantTemp = 300, throttlePosition = 0f).isValid())
        assertTrue(TripMetricLog(timestamp = 0, rpm = 800, speed = 0, coolantTemp = 85, throttlePosition = 12f).isValid())
    }
}
