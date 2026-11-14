package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.data.ReportPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPrivacyTest {

    @Test
    fun vinKeepsLastSix() {
        assertEquals("***********123456", ReportPrivacy.maskVin("1GCGTDEN0H1123456"))
        assertEquals("ECU answered: 40 PIDs supported, VIN ***********123456",
            ReportPrivacy.scrub("ECU answered: 40 PIDs supported, VIN 1GCGTDEN0H1123456"))
    }

    @Test
    fun bluetoothAddressKeepsLastTwoBytes() {
        assertEquals("XX:XX:XX:XX:9A:BC", ReportPrivacy.maskAddress("12:34:56:78:9A:BC"))
        assertEquals("XX-XX-XX-XX-9a-bc", ReportPrivacy.maskAddress("12-34-56-78-9a-bc"))
        assertEquals("Connecting to XX:XX:XX:XX:9A:BC (Veepeak)",
            ReportPrivacy.scrub("Connecting to 12:34:56:78:9A:BC (Veepeak)"))
    }

    @Test
    fun ordinaryTextIsLeftAlone() {
        val line = "01_05 Coolant = 88.00 (2s ago) raw [41 05 80] ABCDEFGHJKLMNPRST"
        assertEquals(line, ReportPrivacy.scrub(line))
    }

    @Test
    fun driverNameIsMasked() {
        assertEquals("Good evening, [driver].", ReportPrivacy.scrub("Good evening, Michael.", "Michael"))
        assertEquals("Good evening.", ReportPrivacy.scrub("Good evening.", ""))
        assertEquals("a b", ReportPrivacy.scrub("a b", "a"))
    }

    @Test
    fun settingsPrintFromAnAllowList() {
        assertEquals("ui_theme = KITT", ReportPrivacy.settingLine("ui_theme", "KITT"))
        assertEquals("wake_word = true", ReportPrivacy.settingLine("wake_word", true))
        assertEquals("driver_name = (not shown)", ReportPrivacy.settingLine("driver_name", "Michael"))
        assertEquals("parking_spot = (not shown)", ReportPrivacy.settingLine("parking_spot", "41.1,-87.2"))
        assertEquals("some_future_secret = (not shown)", ReportPrivacy.settingLine("some_future_secret", "abc"))
        assertEquals("gemini_api_key = (not shown)", ReportPrivacy.settingLine("gemini_api_key", "AIzaXYZ"))
    }

    @Test
    fun maskedSettingsKeepUsefulParts() {
        val adapter = ReportPrivacy.settingLine("obd_adapter_address", "12:34:56:78:9A:BC")
        assertEquals("obd_adapter_address = XX:XX:XX:XX:9A:BC", adapter)
        val identity = ReportPrivacy.settingLine("vehicle_identity", "vin=1GCGTDEN0H1123456\nmake=CHEVROLET\nyear=2017")
        assertTrue(identity.contains("CHEVROLET"))
        assertTrue(identity.contains("***********123456"))
        assertFalse(identity.contains("1GCGTDEN0H1"))
    }
}
