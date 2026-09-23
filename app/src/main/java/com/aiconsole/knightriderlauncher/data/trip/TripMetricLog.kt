package com.aiconsole.knightriderlauncher.data.trip

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One decoded OBD-II sample. Units follow the raw PIDs:
 * speed km/h (01 0D), coolant °C (01 05), throttle % (01 11).
 */
@Entity(tableName = "trip_metric_log", indices = [Index("timestamp")])
data class TripMetricLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val rpm: Int,
    val speed: Int,
    val coolantTemp: Int,
    val throttlePosition: Float
) {
    /** Rejects corrupt frames; bounds are the physical ranges of each PID. */
    fun isValid(): Boolean =
        rpm in 0..16_383 &&
            speed in 0..255 &&
            coolantTemp in -40..215 &&
            throttlePosition in 0f..100f
}

/** Aggregate over a time window, used as the "normal" baseline for this vehicle. */
data class MetricBaseline(
    val sampleCount: Int,
    val avgRpm: Double?,
    val avgSpeed: Double?,
    val avgCoolant: Double?,
    val maxCoolant: Int?,
    val avgThrottle: Double?
)
