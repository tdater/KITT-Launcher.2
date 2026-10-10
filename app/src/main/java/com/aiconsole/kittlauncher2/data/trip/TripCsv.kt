package com.aiconsole.kittlauncher2.data.trip

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Trip logbook as CSV (e.g. for mileage records). Plain Kotlin. */
object TripCsv {

    fun build(trips: List<TripSummary>, metric: Boolean, timeZone: TimeZone = TimeZone.getDefault()): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }
        val time = SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone }
        val dist = if (metric) "km" else "mi"
        val speed = if (metric) "km/h" else "mph"
        val fuel = if (metric) "L" else "gal"
        val economy = if (metric) "L/100km" else "mpg"
        fun d(km: Double) = if (metric) km else km * 0.621371
        return buildString {
            appendLine("date,start,end,distance_$dist,duration_min,moving_min,avg_moving_speed_$speed,max_speed_$speed,fuel_$fuel,economy_$economy")
            trips.sortedBy { it.start }.forEach { t ->
                val fuelUsed = t.fuelLiters?.let { if (metric) it else it / 3.785411784 }
                val econ = if (metric) t.litersPer100Km else t.mpg
                appendLine(
                    listOf(
                        date.format(Date(t.start)),
                        time.format(Date(t.start)),
                        time.format(Date(t.end)),
                        "%.2f".format(Locale.US, d(t.distanceKm)),
                        (t.durationMs / 60_000).toString(),
                        (t.movingMs / 60_000).toString(),
                        "%.1f".format(Locale.US, d(t.avgMovingSpeedKmh)),
                        "%.0f".format(Locale.US, d(t.maxSpeedKmh.toDouble())),
                        fuelUsed?.let { "%.2f".format(Locale.US, it) } ?: "",
                        econ?.let { "%.1f".format(Locale.US, it) } ?: ""
                    ).joinToString(",")
                )
            }
        }
    }
}
