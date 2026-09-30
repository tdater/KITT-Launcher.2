package com.aiconsole.knightriderlauncher.obd

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** CSV layout for recorded sessions: spreadsheet-friendly, US decimal points, one row per sample. */
object CsvLog {

    data class Column(val name: String, val unit: String)

    fun header(columns: List<Column>): String =
        (listOf("time", "elapsed_s") + columns.map { if (it.unit.isBlank()) it.name else "${it.name} (${it.unit})" })
            .joinToString(",") { escape(it) }

    /** [values] are already in display units; null (stale or unsupported) becomes an empty cell. */
    fun row(timestamp: Long, startedAt: Long, values: List<Double?>, timeZone: TimeZone = TimeZone.getDefault()): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply { this.timeZone = timeZone }.format(Date(timestamp))
        val elapsed = "%.1f".format(Locale.US, (timestamp - startedAt) / 1000.0)
        return (listOf(time, elapsed) + values.map { v -> v?.let { format(it) } ?: "" }).joinToString(",")
    }

    fun fileName(startedAt: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
        "kitt-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply { this.timeZone = timeZone }.format(Date(startedAt)) + ".csv"

    private fun format(value: Double): String {
        val text = "%.3f".format(Locale.US, value).trimEnd('0')
        return if (text.endsWith('.')) text.dropLast(1) else text
    }

    private fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
}
