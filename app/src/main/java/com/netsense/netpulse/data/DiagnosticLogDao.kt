package com.netsense.netpulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticLogDao {

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<DiagnosticLogEntity>>

    @Query("SELECT * FROM diagnostic_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<DiagnosticLogEntity>>

    @Query("SELECT * FROM diagnostic_logs WHERE isZombieConnection = 1 ORDER BY timestamp DESC")
    fun getZombieIncidents(): Flow<List<DiagnosticLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DiagnosticLogEntity): Long

    @Query("DELETE FROM diagnostic_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)

    @Query("DELETE FROM diagnostic_logs")
    suspend fun clearAllLogs()
}
