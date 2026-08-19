package com.netsense.netpulse.dataset

import com.netsense.netpulse.data.DailyUsabilityAggregateDao
import com.netsense.netpulse.data.DailyUsabilityAggregateEntity
import com.netsense.netpulse.data.DiagnosticLogDao
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.data.TelemetryObservationDao
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Real retention/aggregation/purge infrastructure - not a "train daily then delete everything"
 * shortcut. Two tables grow unbounded with nothing else in this app ever deleting from them
 * (diagnostic_logs from every probe/Sentinel tick, ml_telemetry_observations from every
 * telemetry cycle - the ~14MB/day source), so this is what keeps the app from bloating in
 * storage while still preserving:
 *   - long-range trend signal (via [DailyUsabilityAggregateEntity], built from real rows
 *     before they're deleted, never interpolated or fabricated)
 *   - any row a label resolution is still actively judging (never purged out from under it -
 *     see [TelemetryObservationDao.purgeExpiredResolved])
 */
class DataLifecycleManager(
    private val diagnosticLogDao: DiagnosticLogDao,
    private val telemetryObservationDao: TelemetryObservationDao,
    private val aggregateDao: DailyUsabilityAggregateDao
) {
    companion object {
        /** Diagnostic log rows older than this are rolled into a daily aggregate. Well short
         *  of the default 30-day retention, so every day gets aggregated long before its raw
         *  rows are eligible for deletion. */
        const val AGGREGATION_AGE_MS = 2L * 24 * 60 * 60 * 1000

        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }

    suspend fun runMaintenance(
        diagnosticRetentionDays: Int,
        telemetryRetentionDays: Int,
        nowMs: Long = System.currentTimeMillis()
    ): MaintenanceResult {
        val toAggregate = diagnosticLogDao.getLogsOlderThan(nowMs - AGGREGATION_AGE_MS)
        val aggregates = aggregateByDay(toAggregate)
        aggregates.forEach { aggregateDao.upsert(it) }

        diagnosticLogDao.deleteLogsOlderThan(nowMs - diagnosticRetentionDays * MS_PER_DAY)
        telemetryObservationDao.purgeExpiredResolved(nowMs - telemetryRetentionDays * MS_PER_DAY)

        return MaintenanceResult(daysAggregated = aggregates.size, ranAtMs = nowMs)
    }
}

data class MaintenanceResult(val daysAggregated: Int, val ranAtMs: Long)

private val UTC_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

/**
 * Groups real [DiagnosticLogEntity] rows into UTC calendar days and computes real
 * min/max/average - a day with zero input rows simply produces no aggregate row, never a
 * zero-filled or interpolated one.
 */
fun aggregateByDay(logs: List<DiagnosticLogEntity>): List<DailyUsabilityAggregateEntity> =
    logs.groupBy { UTC_DATE_FORMAT.format(java.util.Date(it.timestamp)) }
        .map { (dateKey, dayLogs) ->
            val dnsValues = dayLogs.mapNotNull { it.dnsLatencyMs }
            val tcpValues = dayLogs.mapNotNull { it.tcpHandshakeMs }
            val httpValues = dayLogs.mapNotNull { it.httpLatencyMs }
            DailyUsabilityAggregateEntity(
                dateKey = dateKey,
                sampleCount = dayLogs.size,
                averageScore = dayLogs.map { it.score }.average().toInt(),
                minScore = dayLogs.minOf { it.score },
                maxScore = dayLogs.maxOf { it.score },
                zombieCount = dayLogs.count { it.isZombieConnection },
                averageDnsMs = if (dnsValues.isEmpty()) null else dnsValues.average().toLong(),
                averageTcpMs = if (tcpValues.isEmpty()) null else tcpValues.average().toLong(),
                averageHttpMs = if (httpValues.isEmpty()) null else httpValues.average().toLong(),
                firstTimestamp = dayLogs.minOf { it.timestamp },
                lastTimestamp = dayLogs.maxOf { it.timestamp }
            )
        }
