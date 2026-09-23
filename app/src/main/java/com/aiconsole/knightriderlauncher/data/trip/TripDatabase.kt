package com.aiconsole.knightriderlauncher.data.trip

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TripMetricLog::class], version = 1, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {

    abstract fun tripMetricDao(): TripMetricDao

    companion object {
        fun create(context: Context): TripDatabase =
            Room.databaseBuilder(context.applicationContext, TripDatabase::class.java, "trip_metrics.db")
                .build()
    }
}
