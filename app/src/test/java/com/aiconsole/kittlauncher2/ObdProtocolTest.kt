package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.obd.DtcDecoder
import com.aiconsole.kittlauncher2.obd.Elm327
import com.aiconsole.kittlauncher2.obd.FormulaEvaluator
import com.aiconsole.kittlauncher2.obd.ManufacturerProfiles
import com.aiconsole.kittlauncher2.obd.ObdResponseParser
import com.aiconsole.kittlauncher2.obd.PidCatalog
import com.aiconsole.kittlauncher2.ui.scanner.ScannerEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdProtocolTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    // ---- Formula engine -------------------------------------------------------------------

    @Test
    fun formula_rpm() {
        assertEquals(1726.0, FormulaEvaluator.evaluate("((A*256)+B)/4", bytes(0x1A, 0xF8)), 0.001)
    }

    @Test
    fun formula_precedenceAndUnaryMinus() {
        assertEquals(-40.0, FormulaEvaluator.evaluate("A-40", bytes(0)), 0.001)
        assertEquals(14.0, FormulaEvaluator.evaluate("2+3*4", bytes()), 0.001)
        assertEquals(-5.0, FormulaEvaluator.evaluate("-(A+5)", bytes(0)), 0.001)
    }

    @Test
    fun formula_signedFunction() {
        // Ford trans temp: 0xFF 0xF0 → signed(-1)*256+240 = -16 → /16 = -1 °C
        assertEquals(-1.0, FormulaEvaluator.evaluate("((signed(A)*256)+B)/16", bytes(0xFF, 0xF0)), 0.001)
    }

    @Test
    fun formula_errors() {
        assertNotNull(FormulaEvaluator.validate("((A*256)+B"))
        assertNotNull(FormulaEvaluator.validate("foo(A)"))
        assertNull(FormulaEvaluator.validate("min(A,B)/2"))
        assertEquals(2, FormulaEvaluator.bytesUsed("((signed(A)*256)+B)/16"))
    }

    @Test(expected = FormulaEvaluator.FormulaException::class)
    fun formula_missingByteThrows() {
        FormulaEvaluator.evaluate("(A*256)+B", bytes(0x01))
    }

    // ---- Response parsing -----------------------------------------------------------------

    @Test
    fun parser_singleFrameNoSpaces() {
        val messages = ObdResponseParser.messages("410C1AF8")
        assertEquals(1, messages.size)
        assertArrayEquals(bytes(0x41, 0x0C, 0x1A, 0xF8), messages[0])
    }

    @Test
    fun parser_multiPidRequestSplitsByPid() {
        // 010C0D05 → RPM (2 bytes), speed (1), coolant (1)
        val messages = ObdResponseParser.messages("41 0C 1A F8 0D 32 05 7B")
        val values = ObdResponseParser.mode01Values(messages, PidCatalog.mode01ByteCounts)
        assertArrayEquals(bytes(0x1A, 0xF8), values[0x0C])
        assertArrayEquals(bytes(0x32), values[0x0D])
        assertArrayEquals(bytes(0x7B), values[0x05])
    }

    @Test
    fun parser_isoTpMultiFrameVin() {
        // "1FTFW1ET5DFC10312": 49 02 01 + 17 ASCII bytes = 0x014 bytes over three CAN frames.
        val raw = """
            014
            0:490201314654
            1:46573145543544
            2:46433130333132
        """.trimIndent()
        assertEquals("1FTFW1ET5DFC10312", ObdResponseParser.vin(ObdResponseParser.messages(raw)))
    }

    @Test
    fun parser_errorsAndSearching() {
        assertEquals("NO DATA", ObdResponseParser.errorOf("NO DATA"))
        assertEquals("UNABLE TO CONNECT", ObdResponseParser.errorOf("SEARCHING...\nUNABLE TO CONNECT"))
        assertNull(ObdResponseParser.errorOf("SEARCHING...\n4100BE3FA813"))
    }

    @Test
    fun parser_supportedPidBitmap() {
        // BE 1F A8 13 is the classic sample bitmap.
        val supported = ObdResponseParser.supportedPids(ObdResponseParser.messages("4100BE1FA813"), 0x00)
        assertTrue(0x01 in supported)
        assertTrue(0x0C in supported)
        assertTrue(0x0D in supported)
        assertTrue(0x20 in supported)
        assertTrue(0x02 !in supported)
    }

    @Test
    fun parser_serviceDataMode22() {
        val data = ObdResponseParser.serviceData(ObdResponseParser.messages("6219405A"), bytes(0x22, 0x19, 0x40))
        assertArrayEquals(bytes(0x5A), data)
        assertNull(ObdResponseParser.serviceData(ObdResponseParser.messages("7F2231"), bytes(0x22, 0x19, 0x40)))
    }

    @Test
    fun parser_voltage() {
        assertEquals(12.6, ObdResponseParser.voltage("12.6V")!!, 0.001)
        assertNull(ObdResponseParser.voltage("?"))
    }

    // ---- Trouble codes --------------------------------------------------------------------

    @Test
    fun dtc_decode() {
        assertEquals("P0133", DtcDecoder.decode(0x01, 0x33))
        assertEquals("C0300", DtcDecoder.decode(0x43, 0x00))
        assertEquals("U0100", DtcDecoder.decode(0xC1, 0x00))
        assertNull(DtcDecoder.decode(0, 0))
    }

    @Test
    fun dtc_canResponseUsesCountByte() {
        // 43, count=2, P0301, P0171, then padding that must be ignored.
        val codes = ObdResponseParser.dtcs(ObdResponseParser.messages("4302030101710000"), 0x43, isCan = true)
        assertEquals(listOf("P0301", "P0171"), codes)
    }

    @Test
    fun dtc_legacyResponseHasNoCountByte() {
        val codes = ObdResponseParser.dtcs(ObdResponseParser.messages("430301017100000000"), 0x43, isCan = false)
        assertEquals(listOf("P0301", "P0171"), codes)
    }

    @Test
    fun dtc_descriptions() {
        assertEquals("Cylinder 4 misfire detected", DtcDecoder.describe("P0304"))
        assertEquals("Catalyst efficiency below threshold, bank 1", DtcDecoder.describe("P0420"))
        assertTrue(DtcDecoder.describe("P1234").contains("manufacturer-specific"))
    }

    // ---- Manufacturer detection -------------------------------------------------------------

    @Test
    fun manufacturer_longestWmiWins() {
        assertEquals("hyundai", ManufacturerProfiles.detect("5NPE24AF1FH000000")?.id)
        assertEquals("nissan", ManufacturerProfiles.detect("5N1AT2MV0FC000000")?.id)
        assertEquals("ford", ManufacturerProfiles.detect("1FTFW1ET5DFC10312")?.id)
        assertEquals("gm", ManufacturerProfiles.detect("1GCUYDED1LZ000000")?.id)
        assertNull(ManufacturerProfiles.detect(null))
    }

    @Test
    fun tpms_canReceiveHeadersAndDecodersWorkAcrossFormats() {
        // GM BCM 241 replies on 641; Ford BCM 726 replies on 72E; standard 7E0/7DF use default ATAR (null).
        assertEquals("641", Elm327.receiveHeaderFor("241"))
        assertEquals("72E", Elm327.receiveHeaderFor("726"))
        assertEquals("758", Elm327.receiveHeaderFor("750"))
        assertNull(Elm327.receiveHeaderFor("7E0"))
        assertNull(Elm327.receiveHeaderFor("7DF"))

        // 2-byte GM BCM TPMS: 0x0E 0x40 -> 3648 / 16 = 228.0 kPa
        assertEquals(228.0, FormulaEvaluator.evaluate("tpms(A)", bytes(0x0E, 0x40)), 0.01)
        // 1-byte GM BCM TPMS: 0x38 (56) -> 56 * 4 = 224.0 kPa
        assertEquals(224.0, FormulaEvaluator.evaluate("tpms(A)", bytes(0x38)), 0.01)
        // 2-byte GM BCM with status byte: 0x38 0x00 -> 56 * 4 = 224.0 kPa
        assertEquals(224.0, FormulaEvaluator.evaluate("tpms(A)", bytes(0x38, 0x00)), 0.01)

        // Mode 22 reply with 3-digit CAN ID prefix and PCI length byte: "641 05 62 50 05 0E 40"
        val messages = ObdResponseParser.messages("641056250050E40")
        val data = ObdResponseParser.serviceData(messages, bytes(0x22, 0x50, 0x05))
        assertArrayEquals(bytes(0x0E, 0x40), data)

        // Every manufacturer profile (including generic) exposes all four canonical tire pressure PIDs.
        for (profile in ManufacturerProfiles.all) {
            val ids = profile.pids.map { it.id }.toSet()
            for (tireId in PidCatalog.TIRES) {
                assertTrue("${profile.id} missing $tireId", tireId in ids)
            }
        }
    }

    // ---- Scanner light --------------------------------------------------------------------

    @Test
    fun scanner_leavesDecayingTrail() {
        val engine = ScannerEngine(lamps = 8)
        var t = 1_000_000_000L
        engine.step(t)
        repeat(20) {
            t += 16_000_000L // ~60 fps for about a third of a second
            engine.step(t)
        }
        val levels = engine.step(t + 16_000_000L).copyOf()
        val head = levels.indices.maxBy { levels[it] }
        assertTrue(head > 0)
        // Lamp just behind the head still glows, but dimmer than the head.
        assertTrue(levels[head - 1] > 0.05f)
        assertTrue(levels[head - 1] < levels[head])
    }
}
