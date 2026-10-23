package com.aiconsole.kittlauncher2

import com.aiconsole.kittlauncher2.assistant.OfflineAnswers
import com.aiconsole.kittlauncher2.assistant.OfflineFacts
import com.aiconsole.kittlauncher2.obd.PidCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAnswersTest {

    private val overheating = OfflineFacts(
        connected = true,
        readings = mapOf(PidCatalog.COOLANT to 118.0, PidCatalog.RPM to 820.0, PidCatalog.MODULE_VOLTAGE to 13.9, PidCatalog.FUEL_LEVEL to 40.0),
        metric = true,
        storedCodes = listOf("P0217"),
        describe = { if (it == "P0217") "engine coolant over temperature" else "a powertrain fault" }
    )

    private val healthy = overheating.copy(readings = overheating.readings + (PidCatalog.COOLANT to 90.0), storedCodes = emptyList())

    @Test
    fun overheatingIsNeverCalledNormal() {
        // The small offline AI model got exactly this wrong.
        val reply = OfflineAnswers.answer("Is the engine OK?", overheating)!!
        assertTrue(reply, reply.startsWith("Not quite") && "overheating" in reply && "pull over" in reply)
        assertTrue("pull over" in OfflineAnswers.answer("what's the coolant temperature", overheating)!!)
    }

    @Test
    fun readingsCodesAndBattery() {
        assertEquals("Battery is 13.9 volts. That's normal; it's charging.", OfflineAnswers.answer("What's my battery voltage?", healthy))
        assertTrue("P0217 means engine coolant over temperature. It's stored" in OfflineAnswers.answer("What does code p0217 mean?", overheating)!!)
        assertEquals("No fault codes are stored.", OfflineAnswers.answer("any check engine codes?", healthy))
        assertTrue(OfflineAnswers.answer("how's the car doing", healthy)!!.startsWith("Everything looks normal: coolant 90 degrees Celsius"))
        assertTrue("194 degrees Fahrenheit" in OfflineAnswers.answer("engine temp?", healthy.copy(metric = false))!!)
        val charging = healthy.copy(readings = healthy.readings + (PidCatalog.MODULE_VOLTAGE to 11.8))
        assertTrue("isn't charging" in OfflineAnswers.answer("is the alternator charging", charging)!!)
        // A GM smart alternator idling at 12.7 V with a full battery is fine.
        val smart = healthy.copy(readings = healthy.readings + (PidCatalog.MODULE_VOLTAGE to 12.7))
        assertEquals("Battery is 12.7 volts. That's normal; it's charging.", OfflineAnswers.answer("battery voltage?", smart))
    }

    @Test
    fun offTopicAndDisconnected() {
        assertNull(OfflineAnswers.answer("Tell me a joke", healthy))
        // "gasket" isn't a fuel question.
        assertNull(OfflineAnswers.answer("how do I replace a head gasket", healthy))
        assertEquals(OfflineAnswers.NOT_CONNECTED, OfflineAnswers.answer("how much fuel do I have", healthy.copy(connected = false)))
        assertEquals("Fuel is at 40 percent.", OfflineAnswers.answer("how much gas is left", healthy))
    }
}
