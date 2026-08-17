package com.netsense.netpulse.data

import kotlinx.coroutines.flow.Flow

class DiagnosticRepository(
    private val dao: DiagnosticLogDao,
    private val telemetryDao: TelemetryObservationDao? = null
) {

    val allLogs: Flow<List<DiagnosticLogEntity>> = dao.getAllLogs()
    val zombieIncidents: Flow<List<DiagnosticLogEntity>> = dao.getZombieIncidents()

    fun getRecentLogs(limit: Int): Flow<List<DiagnosticLogEntity>> = dao.getRecentLogs(limit)

    suspend fun saveLog(log: DiagnosticLogEntity): Long = dao.insertLog(log)

    suspend fun deleteLog(id: Long) = dao.deleteLogById(id)

    suspend fun clearHistory() = dao.clearAllLogs()

    // ML Telemetry persistence
    suspend fun recordTelemetry(obs: TelemetryObservationEntity): Long {
        return telemetryDao?.insert(obs) ?: -1L
    }

    suspend fun getTelemetryCount(): Int {
        return telemetryDao?.getObservationCount() ?: 0
    }

    suspend fun getRecentTelemetry(limit: Int = 50): List<TelemetryObservationEntity> {
        return telemetryDao?.getRecentObservations(limit) ?: emptyList()
    }
}

