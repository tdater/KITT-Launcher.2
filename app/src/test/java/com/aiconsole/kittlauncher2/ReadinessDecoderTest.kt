package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.obd.Readiness
import com.aiconsole.kittlauncher2.obd.ReadinessDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessDecoderTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun gasolineAllComplete() {
        // MIL off, 0 codes; misfire/fuel/components supported and complete;
        // catalyst, evap, O2 sensor, O2 heater supported (0x65) and complete.
        val r = ReadinessDecoder.decode(bytes(0x00, 0x07, 0x65, 0x00))!!
        assertFalse(r.milOn)
        assertFalse(r.compressionIgnition)
        assertEquals(
            listOf("Misfire", "Fuel system", "Comprehensive components", "Catalyst", "Evaporative system", "Oxygen sensor", "Oxygen sensor heater"),
            r.monitors.map { it.name }
        )
        assertTrue(r.monitors.all { it.complete })
        assertEquals(Readiness.Verdict.Ready, r.verdict)
    }

    @Test
    fun incompleteBitsMarkMonitorsAndDriveTheVerdict() {
        // Catalyst (bit 0) and evap (bit 2) incomplete.
        val r = ReadinessDecoder.decode(bytes(0x00, 0x07, 0x65, 0x05))!!
        assertEquals(listOf("Catalyst", "Evaporative system"), r.incomplete.map { it.name })
        assertEquals(Readiness.Verdict.NotReady, r.verdict)

        val one = ReadinessDecoder.decode(bytes(0x00, 0x07, 0x65, 0x04))!!
        assertEquals(Readiness.Verdict.LikelyReady, one.verdict)
    }

    @Test
    fun commonMonitorIncompleteUsesTheHighNibble() {
        // Misfire supported (bit 0) and incomplete (bit 4).
        val r = ReadinessDecoder.decode(bytes(0x00, 0x11, 0x00, 0x00))!!
        assertEquals(listOf("Misfire"), r.monitors.map { it.name })
        assertFalse(r.monitors.single().complete)
    }

    @Test
    fun milOnIsNeverReadyAndCountsCodes() {
        val r = ReadinessDecoder.decode(bytes(0x82, 0x07, 0x01, 0x00))!!
        assertTrue(r.milOn)
        assertEquals(2, r.dtcCount)
        assertEquals(Readiness.Verdict.NotReady, r.verdict)
    }

    @Test
    fun dieselUsesTheCompressionMonitorSet() {
        // Bit 3 of B = compression ignition; NOx/SCR (bit 1) and particulate filter (bit 6) supported.
        val r = ReadinessDecoder.decode(bytes(0x00, 0x0F, 0x42, 0x40))!!
        assertTrue(r.compressionIgnition)
        assertEquals(
            listOf("Misfire", "Fuel system", "Comprehensive components", "NOx / SCR aftertreatment", "Particulate filter"),
            r.monitors.map { it.name }
        )
        assertEquals(listOf("Particulate filter"), r.incomplete.map { it.name })
    }

    @Test
    fun shortReplyIsRejected() {
        assertNull(ReadinessDecoder.decode(bytes(0x00, 0x07)))
    }
}
