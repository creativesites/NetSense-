package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DiagnosticLogEntity::class, TelemetryObservationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NetPulseDatabase : RoomDatabase() {

    abstract fun diagnosticLogDao(): DiagnosticLogDao
    abstract fun telemetryObservationDao(): TelemetryObservationDao

    companion object {
        @Volatile
        private var INSTANCE: NetPulseDatabase? = null

        fun getDatabase(context: Context): NetPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NetPulseDatabase::class.java,
                    "netpulse_database.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
