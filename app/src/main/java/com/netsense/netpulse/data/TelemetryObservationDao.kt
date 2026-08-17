package com.netsense.netpulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryObservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: TelemetryObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(observations: List<TelemetryObservationEntity>)

    @Query("SELECT * FROM ml_telemetry_observations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentObservations(limit: Int = 100): List<TelemetryObservationEntity>

    @Query("SELECT * FROM ml_telemetry_observations ORDER BY timestamp DESC")
    fun getAllObservationsFlow(): Flow<List<TelemetryObservationEntity>>

    @Query("SELECT COUNT(*) FROM ml_telemetry_observations")
    suspend fun getObservationCount(): Int

    @Query("DELETE FROM ml_telemetry_observations WHERE timestamp < :cutoffTimestamp")
    suspend fun purgeOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM ml_telemetry_observations")
    suspend fun clearAll()
}
