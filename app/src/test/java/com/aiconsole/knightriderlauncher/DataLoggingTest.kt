package com.aiconsole.knightriderlauncher

import com.aiconsole.knightriderlauncher.obd.CsvLog
import com.aiconsole.knightriderlauncher.obd.PidHistory
import com.aiconsole.knightriderlauncher.obd.PidReading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class DataLoggingTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun historyDropsDuplicatesAndOldPoints() {
        val history = PidHistory(windowMs = 10_000L)
        history.add("rpm", PidReading(800.0, 1_000L))
        history.add("rpm", PidReading(900.0, 1_000L)) // same timestamp: ignored
        history.add("rpm", PidReading(1_000.0, 5_000L))
        history.add("rpm", PidReading(1_200.0, 12_000L)) // pushes 1_000L out of the 10 s window
        assertEquals(listOf(1_000.0, 1_200.0), history.points("rpm", 12_000L, 60_000L).map { it.value })
    }

    @Test
    fun historyQueryHonoursTheRequestedSpan() {
        val history = PidHistory()
        (0..10).forEach { history.add("spd", PidReading(it.toDouble(), it * 1_000L)) }
        assertEquals(listOf(8.0, 9.0, 10.0), history.points("spd", 10_000L, 2_000L).map { it.value })
        assertEquals(emptyList<PidHistory.Point>(), history.points("missing", 10_000L, 2_000L))
    }

    @Test
    fun historyCapsPointCount() {
        val history = PidHistory(windowMs = Long.MAX_VALUE / 2, maxPoints = 3)
        (1..5).forEach { history.add("x", PidReading(it.toDouble(), it.toLong())) }
        assertEquals(listOf(3.0, 4.0, 5.0), history.points("x", 5L, 100L).map { it.value })
    }

    @Test
    fun csvHeaderQuotesAndAddsUnits() {
        val header = CsvLog.header(listOf(CsvLog.Column("Engine RPM", "rpm"), CsvLog.Column("Fuel, level", "%"), CsvLog.Column("Status", "")))
        assertEquals("time,elapsed_s,Engine RPM (rpm),\"Fuel, level (%)\",Status", header)
    }

    @Test
    fun csvRowFormatsTimeElapsedAndBlanks() {
        val row = CsvLog.row(timestamp = 1_500L, startedAt = 0L, values = listOf(812.5, null, 2.0, 0.1234), timeZone = utc)
        assertEquals("1970-01-01 00:00:01.500,1.5,812.5,,2,0.123", row)
    }

    @Test
    fun csvFileNameIsSortableAndSafe() {
        assertEquals("kitt-log-19700101-000000.csv", CsvLog.fileName(0L, utc))
    }
}
