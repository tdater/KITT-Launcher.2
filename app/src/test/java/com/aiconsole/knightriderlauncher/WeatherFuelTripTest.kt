package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.data.trip.TripCsv
import com.aiconsole.knightriderlauncher.data.trip.TripSummary
import com.aiconsole.knightriderlauncher.nav.Weather
import com.aiconsole.knightriderlauncher.nav.WeatherRules
import com.aiconsole.knightriderlauncher.obd.FillUp
import com.aiconsole.knightriderlauncher.obd.FuelLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class WeatherFuelTripTest {

    private fun w(temp: Double, code: Int, precip: Double? = 0.0, wind: Double? = 10.0) = Weather(temp, code, wind, precip, 0)

    @Test
    fun weatherWarningsMostSeriousFirst() {
        assertTrue(WeatherRules.roadWarning(w(-2.0, 66))!!.startsWith("Freezing rain"))
        assertTrue(WeatherRules.roadWarning(w(0.5, 61, precip = 0.4))!!.contains("black ice"))
        assertTrue(WeatherRules.roadWarning(w(0.0, 0))!!.contains("near freezing"))
        assertEquals("Fog. Reduced visibility.", WeatherRules.roadWarning(w(10.0, 45)))
        assertTrue(WeatherRules.roadWarning(w(20.0, 0, wind = 70.0))!!.startsWith("Strong winds"))
        assertNull(WeatherRules.roadWarning(w(20.0, 1)))
        assertEquals("Thunderstorm, hail", WeatherRules.describe(99))
    }

    @Test
    fun fuelEconomyIsFullToFullIncludingPartials() {
        val fills = listOf(
            FillUp(1, 10_000.0, 60.0, 90.0, full = true),
            FillUp(2, 10_300.0, 20.0, 30.0, full = false),
            FillUp(3, 10_600.0, 40.0, 62.0, full = true),
            FillUp(4, 11_100.0, 50.0, 80.0, full = true)
        )
        val economy = FuelLog.economy(fills)
        assertEquals(2, economy.size)
        assertEquals(10.0, economy[0].litersPer100Km, 1e-9) // 60 L over 600 km
        assertEquals(10.0, economy[1].litersPer100Km, 1e-9) // 50 L over 500 km
        assertEquals(10.0, FuelLog.average(economy)!!, 1e-9)
        assertEquals(172.0 / 1_100.0, FuelLog.costPerKm(fills)!!, 1e-9)
        assertEquals(fills, FuelLog.decode(FuelLog.encode(fills)))
    }

    @Test
    fun tripCsvHasHeaderAndConvertedUnits() {
        val trip = TripSummary(start = 0, end = 30 * 60_000L, distanceKm = 32.18688, movingMs = 25 * 60_000L, idleMs = 5 * 60_000L, maxSpeedKmh = 100, fuelLiters = 3.785411784)
        val csv = TripCsv.build(listOf(trip), metric = false, timeZone = TimeZone.getTimeZone("UTC")).lines()
        assertTrue(csv[0].startsWith("date,start,end,distance_mi"))
        assertEquals("1970-01-01,00:00,00:30,20.00,30,25,48.0,62,1.00,20.0", csv[1])
    }
}
