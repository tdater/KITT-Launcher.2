package com.aiconsole.kittlauncher2.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TripMetricDao {

    @Insert
    suspend fun insert(log: TripMetricLog): Long

    /** Batched insert for streaming frames; runs in a single transaction. */
    @Insert
    suspend fun insertAll(logs: List<TripMetricLog>)

    @Query("SELECT AVG(coolantTemp) FROM trip_metric_log WHERE timestamp >= :since")
    suspend fun averageCoolantSince(since: Long): Double?

    @Query(
        """
        SELECT COUNT(*) AS sampleCount,
               AVG(rpm) AS avgRpm,
               AVG(speed) AS avgSpeed,
               AVG(coolantTemp) AS avgCoolant,
               MAX(coolantTemp) AS maxCoolant,
               AVG(throttlePosition) AS avgThrottle
        FROM trip_metric_log
        WHERE timestamp >= :since
        """
    )
    suspend fun baselineSince(since: Long): MetricBaseline

    @Query("SELECT timestamp, speed, fuelRate FROM trip_metric_log WHERE timestamp >= :since ORDER BY timestamp")
    suspend fun samplesSince(since: Long): List<TripSample>

    @Query("SELECT * FROM trip_metric_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<TripMetricLog>

    /** Retention pruning; returns rows removed. */
    @Query("DELETE FROM trip_metric_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
