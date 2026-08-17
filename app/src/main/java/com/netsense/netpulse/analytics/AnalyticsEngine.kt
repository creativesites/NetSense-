package com.netsense.netpulse.analytics

import com.netsense.netpulse.data.DiagnosticLogEntity
import kotlin.math.roundToInt

data class OutageIncident(
    val id: Long,
    val timestamp: Long,
    val durationFormatted: String,
    val triggerReason: String,
    val transport: String,
    val carrierName: String?
)

data class UsabilityAnalyticsSummary(
    val totalProbes: Int = 0,
    val averageScore: Int = 0,
    val zombieCount: Int = 0,
    val uptimePercentage: Float = 100f,
    val averageDnsMs: Long = 0,
    val averageTcpMs: Long = 0,
    val averageHttpMs: Long = 0,
    val scoreTrend: List<Int> = emptyList(),
    val zombieIncidents: List<OutageIncident> = emptyList()
)

object AnalyticsEngine {

    fun generateAnalytics(logs: List<DiagnosticLogEntity>): UsabilityAnalyticsSummary {
        if (logs.isEmpty()) return UsabilityAnalyticsSummary()

        val totalProbes = logs.size
        val avgScore = logs.map { it.score }.average().roundToInt()
        val zombieLogs = logs.filter { it.isZombieConnection }
        val usableLogs = logs.filter { it.score >= 35 }
        val uptime = ((usableLogs.size.toFloat() / totalProbes.toFloat()) * 100f).coerceIn(0f, 100f)

        val dnsLogs = logs.mapNotNull { it.dnsLatencyMs }
        val avgDns = if (dnsLogs.isNotEmpty()) dnsLogs.average().toLong() else 0L

        val tcpLogs = logs.mapNotNull { it.tcpHandshakeMs }
        val avgTcp = if (tcpLogs.isNotEmpty()) tcpLogs.average().toLong() else 0L

        val httpLogs = logs.mapNotNull { it.httpLatencyMs }
        val avgHttp = if (httpLogs.isNotEmpty()) httpLogs.average().toLong() else 0L

        val trend = logs.take(15).map { it.score }.reversed()

        val incidents = zombieLogs.take(10).map { z ->
            OutageIncident(
                id = z.id,
                timestamp = z.timestamp,
                durationFormatted = "Detected ~${z.durationMs}ms probe",
                triggerReason = z.primaryDiagnosis,
                transport = z.transport,
                carrierName = z.carrierName
            )
        }

        return UsabilityAnalyticsSummary(
            totalProbes = totalProbes,
            averageScore = avgScore,
            zombieCount = zombieLogs.size,
            uptimePercentage = uptime,
            averageDnsMs = avgDns,
            averageTcpMs = avgTcp,
            averageHttpMs = avgHttp,
            scoreTrend = trend,
            zombieIncidents = incidents
        )
    }
}
