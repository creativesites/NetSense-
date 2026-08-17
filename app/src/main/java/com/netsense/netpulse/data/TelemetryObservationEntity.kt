package com.netsense.netpulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ml_telemetry_observations")
data class TelemetryObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val transport: String,
    val networkId: String? = null,
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val sinrDb: Int? = null,
    val wifiRssiDbm: Int? = null,
    val dnsLatencyMs: Long? = null,
    val dnsSuccess: Boolean,
    val tcpRttMs: Long? = null,
    val tcpSuccess: Boolean,
    val tcpJitterMs: Long? = null,
    val httpTtfbMs: Long? = null,
    val httpSuccess: Boolean,
    val packetLossPct: Float,
    val consecutiveProbeFailures: Int,
    val usabilityScore: Int,
    val isValidated: Boolean,
    val isZombie: Boolean,
    val primaryDiagnosis: String,
    val recoveryActionTriggered: String? = null,
    val recoverySuccess: Boolean? = null,
    // Future training labels derived after window:
    val labelDegradation15s: Boolean? = null,
    val labelDropout30s: Boolean? = null,
    val labelAnomaly: Boolean? = null,
    val labelLikelyCause: String? = null
)
