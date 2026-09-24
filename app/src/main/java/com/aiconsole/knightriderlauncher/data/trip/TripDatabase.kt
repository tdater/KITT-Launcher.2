package com.aiconsole.knightriderlauncher.data.trip

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TripMetricLog::class], version = 2, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {

    abstract fun tripMetricDao(): TripMetricDao

    companion object {
        /** v2: fuel rate (L/h) per sample, for trip economy. Nullable, so old rows stay valid. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_metric_log ADD COLUMN fuelRate REAL")
            }
        }

        fun create(context: Context): TripDatabase =
            Room.databaseBuilder(context.applicationContext, TripDatabase::class.java, "trip_metrics.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
