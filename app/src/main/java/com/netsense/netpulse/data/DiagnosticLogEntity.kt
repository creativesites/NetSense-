package com.netsense.netpulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val score: Int,
    val rating: String,
    val classification: String,
    val isZombieConnection: Boolean,
    val primaryDiagnosis: String,
    val rootCauseSummary: String,
    val transport: String,
    val carrierName: String?,
    val cellularNetworkType: String?,
    val signalLevel: Int?,
    val signalDbm: Int?,
    val dnsLatencyMs: Long?,
    val dnsSuccess: Boolean,
    val tcpHandshakeMs: Long?,
    val tcpSuccess: Boolean,
    val tcpJitterMs: Long?,
    val httpLatencyMs: Long?,
    val httpSuccess: Boolean,
    val httpStatusCode: Int?,
    val packetLossPct: Float,
    val durationMs: Long,
    val probeMode: String,
    val notes: String? = null
)
