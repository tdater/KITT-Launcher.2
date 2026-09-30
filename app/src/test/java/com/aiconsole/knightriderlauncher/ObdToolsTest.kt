package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.obd.BatteryHealth
import com.aiconsole.knightriderlauncher.obd.CloneCheck
import com.aiconsole.knightriderlauncher.obd.ConsoleSafety
import com.aiconsole.knightriderlauncher.obd.FuelMath
import com.aiconsole.knightriderlauncher.obd.Maintenance
import com.aiconsole.knightriderlauncher.obd.MaintenanceItem
import com.aiconsole.knightriderlauncher.obd.Mode06
import com.aiconsole.knightriderlauncher.obd.ModuleScan
import com.aiconsole.knightriderlauncher.obd.PerformanceRun
import com.aiconsole.knightriderlauncher.obd.PerformanceTimer
import com.aiconsole.knightriderlauncher.obd.PidHistory
import com.aiconsole.knightriderlauncher.obd.VinDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdToolsTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun mode06ParsesRecordsAndPassFail() {
        // Cylinder 1 misfires this drive: 3 counts, limits 0..65535 (UASID 0x24 counts).
        // Catalyst B1 test 0x80: raw 0x0100 with limits 0..0x00FF -> fails.
        val msg = bytes(
            0x46,
            0xA2, 0x0C, 0x24, 0x00, 0x03, 0x00, 0x00, 0xFF, 0xFF,
            0x21, 0x80, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF
        )
        val results = Mode06.results(listOf(msg))
        assertEquals(2, results.size)
        val misfire = results[0]
        assertEquals("Misfire cylinder 1", misfire.monitor)
        assertEquals(3.0, misfire.value, 0.0)
        assertTrue(misfire.passed)
        assertEquals("Catalyst bank 1", results[1].monitor)
        assertFalse(results[1].passed)
        assertEquals(mapOf(1 to 3), Mode06.misfiresByCylinder(results))
    }

    @Test
    fun mode06SignedAndScaledUnits() {
        // UASID 0x8B = signed volts x0.001: value -0.5 V within -1..1 V.
        val msg = bytes(0x46, 0x01, 0x01, 0x8B, 0xFE, 0x0C, 0xFC, 0x18, 0x03, 0xE8)
        val r = Mode06.results(listOf(msg)).single()
        assertEquals(-0.5, r.value, 1e-9)
        assertEquals(-1.0, r.min, 1e-9)
        assertEquals("V", r.unit)
        assertTrue(r.passed)
        assertEquals("O2 sensor B1S1", r.monitor)
    }

    @Test
    fun mode06SupportedMidBitmap() {
        val msg = bytes(0x46, 0x00, 0x80, 0x00, 0x00, 0x01)
        assertEquals(setOf(0x01, 0x20), Mode06.supportedMids(listOf(msg), 0x00))
    }

    @Test
    fun moduleScanFindsElevenAndTwentyNineBitResponders() {
        val raw = "7E8 06 41 00 BE 3F A8 13\n7E9 06 41 00 98 18 80 11\n7E8 06 41 00 BE 3F A8 13\nSEARCHING..."
        val modules = ModuleScan.responders(raw)
        assertEquals(listOf("7E8", "7E9"), modules.map { it.responseId })
        assertEquals("Engine (ECM)", modules[0].name)
        assertEquals("7E1", modules[1].requestHeader)
        assertEquals(listOf("18DAF110"), ModuleScan.responders("18 DA F1 10 06 41 00 BE 3F A8 13").map { it.responseId })
    }

    @Test
    fun consoleAllowsReadsAndBlocksWrites() {
        assertNull(ConsoleSafety.check("010C"))
        assertNull(ConsoleSafety.check("03"))
        assertNull(ConsoleSafety.check("22 F1 90"))
        assertNull(ConsoleSafety.check("ATRV"))
        assertNull(ConsoleSafety.check("STI"))
        assertNotNull(ConsoleSafety.check("04")) // clear codes
        assertNotNull(ConsoleSafety.check("2E F1 90 00")) // write data by id
        assertNotNull(ConsoleSafety.check("3101FF00")) // routine control
        assertNotNull(ConsoleSafety.check("1101")) // ECU reset
        assertNotNull(ConsoleSafety.check("ATPP 0C SV 23")) // adapter EEPROM
        assertNotNull(ConsoleSafety.check("ATMA"))
        assertNotNull(ConsoleSafety.check("hello"))
        assertNotNull(ConsoleSafety.check("010"))
    }

    @Test
    fun cloneCheckFlagsImpossibleVersions() {
        assertTrue(CloneCheck.assess("ELM327 v1.5", null, null).verdict.startsWith("Clone"))
        assertTrue(CloneCheck.assess("ELM327 v2.1", "OBDII to RS232 Interpreter", "?").verdict.contains("clones"))
        assertTrue(CloneCheck.assess("ELM327 v1.4b", null, "STN2255 v5.6.19").verdict.startsWith("STN"))
    }

    @Test
    fun vinYearCheckDigitAndValidity() {
        // Well-known valid check-digit example.
        val b = VinDecoder.basics("1M8GDM9AXKP042788")!!
        assertEquals(true, b.checkDigitValid)
        assertEquals(1989, b.modelYear)
        assertEquals("North America", b.region)
        // Position 7 letter -> 2010+ cycle: 'L' = 2020.
        assertEquals(2020, VinDecoder.modelYear("1GCGTCEN0L1234567"))
        assertFalse(VinDecoder.isValid("1GCGTCEN0L123456O"))
        assertNull(VinDecoder.basics("short"))
    }

    @Test
    fun batteryReadsRestingCrankingAndCharging() {
        val rpm = listOf(PidHistory.Point(0, 0.0), PidHistory.Point(4_000, 0.0), PidHistory.Point(5_000, 700.0), PidHistory.Point(9_000, 750.0))
        val volts = listOf(
            PidHistory.Point(1_000, 12.5),
            PidHistory.Point(3_500, 9.2),
            PidHistory.Point(4_500, 10.8),
            PidHistory.Point(8_000, 14.3)
        )
        val r = BatteryHealth.analyze(volts, rpm)
        assertEquals(10.8, r.restingVolts!!, 0.0)
        assertEquals(9.2, r.crankingMinVolts!!, 0.0)
        assertEquals(14.3, r.chargingVolts!!, 0.0)
        assertTrue(r.crankingState!!.startsWith("Weak"))
        assertEquals("Charging normally", r.chargingState)
        assertEquals(2, r.warnings.size)
    }

    @Test
    fun zeroToSixtyTimesTheRun() {
        val t = PerformanceTimer(PerformanceRun.ZeroToSixty, metric = false)
        t.feed(0, 0.0)
        assertEquals(PerformanceTimer.Phase.Armed, t.phase)
        // Constant 10 km/h/s acceleration from t=1s.
        for (i in 0..120) t.feed(1_000L + i * 100, i.toDouble())
        val r = t.result!!
        assertEquals(9.7, r.seconds, 0.11)
        assertTrue(r.distanceM > 120 && r.distanceM < 140)
    }

    @Test
    fun brakingArmsAboveTargetAndStopsAtZero() {
        val t = PerformanceTimer(PerformanceRun.SixtyToZero, metric = true)
        t.feed(0, 110.0)
        assertEquals(PerformanceTimer.Phase.Armed, t.phase)
        var speed = 105.0
        var time = 100L
        while (speed >= 0) {
            t.feed(time, speed)
            speed -= 3.0
            time += 100
        }
        t.feed(time, 0.0)
        assertEquals(PerformanceTimer.Phase.Done, t.phase)
        assertNotNull(t.result)
    }

    @Test
    fun maintenanceStatusAndStorage() {
        val oil = MaintenanceItem("Oil change", 8_000.0, 100_000.0)
        assertEquals(MaintenanceItem.Status.Ok, oil.status(105_000.0))
        assertEquals(MaintenanceItem.Status.DueSoon, oil.status(107_500.0))
        assertEquals(MaintenanceItem.Status.Overdue, oil.status(108_100.0))
        val items = listOf(oil, MaintenanceItem("Rotate|tires", 10_000.0, 100_000.0))
        val round = Maintenance.decode(Maintenance.encode(items))
        assertEquals(listOf("Oil change", "Rotate/tires"), round.map { it.name })
        assertEquals("Oil change overdue by 100 km", Maintenance.reminder(round, 108_100.0, metric = true))
        assertNull(Maintenance.reminder(round, 101_000.0, metric = true))
    }

    @Test
    fun fuelRange() {
        assertEquals(300.0, FuelMath.rangeKm(50.0, 60.0, 10.0)!!, 1e-9)
        assertNull(FuelMath.rangeKm(50.0, 0.0, 10.0))
    }
}

class AdapterProfilesTest {
    @Test
    fun roundTripsAndDropsBadLines() {
        val list = listOf(
            com.aiconsole.knightriderlauncher.obd.AdapterConfig(com.aiconsole.knightriderlauncher.obd.AdapterType.BLE, "AA:BB", "Veepeak | BLE"),
            com.aiconsole.knightriderlauncher.obd.AdapterConfig(com.aiconsole.knightriderlauncher.obd.AdapterType.CLASSIC, "CC:DD", "OBDAi")
        )
        val text = com.aiconsole.knightriderlauncher.obd.AdapterProfiles.encode(list) + "\nGARBAGE\nWIFI||x"
        assertEquals(list, com.aiconsole.knightriderlauncher.obd.AdapterProfiles.decode(text))
    }
}
