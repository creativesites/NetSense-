package com.netsense.netpulse

import com.netsense.netpulse.data.DailyUsabilityAggregateDao
import com.netsense.netpulse.data.DailyUsabilityAggregateEntity
import com.netsense.netpulse.data.DiagnosticLogDao
import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.data.TelemetryObservationDao
import com.netsense.netpulse.data.TelemetryObservationEntity
import com.netsense.netpulse.dataset.DataLifecycleManager
import com.netsense.netpulse.dataset.aggregateByDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun log(id: Long, timestamp: Long, score: Int, isZombie: Boolean = false) = DiagnosticLogEntity(
    id = id,
    timestamp = timestamp,
    score = score,
    rating = "GOOD",
    classification = "INTERNET_VALIDATED",
    isZombieConnection = isZombie,
    primaryDiagnosis = "test",
    rootCauseSummary = "test",
    transport = "WIFI",
    carrierName = null,
    cellularNetworkType = null,
    signalLevel = null,
    signalDbm = null,
    dnsLatencyMs = 20L,
    dnsSuccess = true,
    tcpHandshakeMs = 40L,
    tcpSuccess = true,
    tcpJitterMs = null,
    httpLatencyMs = 80L,
    httpSuccess = true,
    httpStatusCode = 204,
    packetLossPct = 0f,
    durationMs = 100,
    probeMode = "MICRO"
)

class AggregateByDayTest {

    @Test
    fun `empty input produces no aggregate rows`() {
        assertTrue(aggregateByDay(emptyList()).isEmpty())
    }

    @Test
    fun `rows on the same UTC day are grouped into one real average, never fabricated`() {
        val dayStartMs = 1_700_000_000_000L // arbitrary fixed instant
        val logs = listOf(
            log(1, dayStartMs, score = 60),
            log(2, dayStartMs + 60_000L, score = 80, isZombie = true)
        )
        val aggregates = aggregateByDay(logs)
        assertEquals(1, aggregates.size)
        val day = aggregates.first()
        assertEquals(2, day.sampleCount)
        assertEquals(70, day.averageScore)
        assertEquals(60, day.minScore)
        assertEquals(80, day.maxScore)
        assertEquals(1, day.zombieCount)
        assertEquals(20L, day.averageDnsMs)
    }

    @Test
    fun `rows on different days produce separate aggregate rows, one per real day`() {
        val day1 = 1_700_000_000_000L
        val day2 = day1 + 2L * 24 * 60 * 60 * 1000 // +2 days, safely past midnight
        val logs = listOf(log(1, day1, score = 50), log(2, day2, score = 90))
        val aggregates = aggregateByDay(logs)
        assertEquals(2, aggregates.size)
    }

    @Test
    fun `a day with no dns samples reports null average, never zero-filled`() {
        val ts = 1_700_000_000_000L
        val noDns = log(1, ts, score = 70).copy(dnsLatencyMs = null, dnsSuccess = false)
        val aggregates = aggregateByDay(listOf(noDns))
        assertNull(aggregates.first().averageDnsMs)
    }
}

/** In-memory fakes so DataLifecycleManager can be tested as pure orchestration logic. */
private class FakeDiagnosticLogDao : DiagnosticLogDao {
    val rows = mutableListOf<DiagnosticLogEntity>()
    var deleteOlderThanCalledWith: Long? = null

    override fun getAllLogs(): Flow<List<DiagnosticLogEntity>> = MutableStateFlow(rows)
    override fun getRecentLogs(limit: Int): Flow<List<DiagnosticLogEntity>> = MutableStateFlow(rows)
    override fun getZombieIncidents(): Flow<List<DiagnosticLogEntity>> = MutableStateFlow(rows)
    override suspend fun insertLog(log: DiagnosticLogEntity): Long { rows.add(log); return log.id }
    override suspend fun deleteLogById(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun clearAllLogs() { rows.clear() }
    override suspend fun getLogsOlderThan(cutoffTimestamp: Long): List<DiagnosticLogEntity> =
        rows.filter { it.timestamp < cutoffTimestamp }
    override suspend fun deleteLogsOlderThan(cutoffTimestamp: Long) {
        deleteOlderThanCalledWith = cutoffTimestamp
        rows.removeAll { it.timestamp < cutoffTimestamp }
    }
    override suspend fun getLogCount(): Int = rows.size
}

private class FakeTelemetryObservationDao : TelemetryObservationDao {
    val rows = mutableListOf<TelemetryObservationEntity>()
    var purgeExpiredResolvedCalledWith: Long? = null

    override suspend fun insert(observation: TelemetryObservationEntity): Long { rows.add(observation); return observation.id }
    override suspend fun insertAll(observations: List<TelemetryObservationEntity>) { rows.addAll(observations) }
    override suspend fun getRecentObservations(limit: Int): List<TelemetryObservationEntity> = rows.take(limit)
    override fun getAllObservationsFlow(): Flow<List<TelemetryObservationEntity>> = MutableStateFlow(rows)
    override suspend fun getObservationCount(): Int = rows.size
    override suspend fun purgeOlderThan(cutoffTimestamp: Long) { rows.removeAll { it.timestamp < cutoffTimestamp } }
    override suspend fun purgeExpiredResolved(cutoffTimestamp: Long) {
        purgeExpiredResolvedCalledWith = cutoffTimestamp
        rows.removeAll {
            it.timestamp < cutoffTimestamp &&
                it.labelDegradation15sStatus != "UNRESOLVED" &&
                it.labelDropout30sStatus != "UNRESOLVED"
        }
    }
    override suspend fun clearAll() { rows.clear() }
    override suspend fun getObservationsForSession(sessionId: String): List<TelemetryObservationEntity> =
        rows.filter { it.sessionId == sessionId }
    override suspend fun getSessionIdsWithUnresolvedLabels(): List<String> = emptyList()
    override suspend fun updateResolvedLabels(
        id: Long, degradation: Boolean?, degradationStatus: String,
        dropout: Boolean?, dropoutStatus: String, cause: String?, causeStatus: String, schemaVersion: Int
    ) {}
    override suspend fun getProductionObservations(): List<TelemetryObservationEntity> = rows.filter { !it.isSynthetic }
    override fun getProductionObservationsFlow(): Flow<List<TelemetryObservationEntity>> = MutableStateFlow(rows)
    override suspend fun getProductionSessionIds(): List<String> = rows.map { it.sessionId }.distinct()
}

private class FakeDailyUsabilityAggregateDao : DailyUsabilityAggregateDao {
    val rows = mutableMapOf<String, DailyUsabilityAggregateEntity>()
    override suspend fun upsert(aggregate: DailyUsabilityAggregateEntity) { rows[aggregate.dateKey] = aggregate }
    override suspend fun getAll(): List<DailyUsabilityAggregateEntity> = rows.values.sortedBy { it.dateKey }
    override fun getAllFlow(): Flow<List<DailyUsabilityAggregateEntity>> = MutableStateFlow(rows.values.toList())
    override suspend fun getCount(): Int = rows.size
    override suspend fun clearAll() { rows.clear() }
}

private fun observation(id: Long, timestamp: Long, labelStatus: String = "RESOLVED") = TelemetryObservationEntity(
    id = id,
    timestamp = timestamp,
    sessionId = "session-1",
    transport = "WIFI",
    dnsSuccess = true,
    tcpSuccess = true,
    httpSuccess = true,
    packetLossPct = 0f,
    consecutiveProbeFailures = 0,
    usabilityScore = 80,
    isValidated = true,
    isZombie = false,
    primaryDiagnosis = "test",
    observationQuality = "HIGH",
    labelDegradation15sStatus = labelStatus,
    labelDropout30sStatus = labelStatus
)

class DataLifecycleManagerTest {

    @Test
    fun `old diagnostic logs are aggregated before being purged`() = runBlocking {
        val diagnosticDao = FakeDiagnosticLogDao()
        val telemetryDao = FakeTelemetryObservationDao()
        val aggregateDao = FakeDailyUsabilityAggregateDao()
        val manager = DataLifecycleManager(diagnosticDao, telemetryDao, aggregateDao)

        val now = 1_700_000_000_000L
        val oldTimestamp = now - 40L * 24 * 60 * 60 * 1000 // 40 days old
        diagnosticDao.rows.add(log(1, oldTimestamp, score = 55))

        manager.runMaintenance(diagnosticRetentionDays = 30, telemetryRetentionDays = 14, nowMs = now)

        assertEquals(1, aggregateDao.rows.size)
        assertTrue("old row past retention should be purged", diagnosticDao.rows.isEmpty())
    }

    @Test
    fun `diagnostic logs within retention are aggregated but not deleted`() = runBlocking {
        val diagnosticDao = FakeDiagnosticLogDao()
        val telemetryDao = FakeTelemetryObservationDao()
        val aggregateDao = FakeDailyUsabilityAggregateDao()
        val manager = DataLifecycleManager(diagnosticDao, telemetryDao, aggregateDao)

        val now = 1_700_000_000_000L
        val threeDaysAgo = now - 3L * 24 * 60 * 60 * 1000
        diagnosticDao.rows.add(log(1, threeDaysAgo, score = 55))

        manager.runMaintenance(diagnosticRetentionDays = 30, telemetryRetentionDays = 14, nowMs = now)

        assertEquals(1, aggregateDao.rows.size)
        assertEquals(1, diagnosticDao.rows.size)
    }

    @Test
    fun `expired telemetry with a still-unresolved label is never purged out from under label resolution`() = runBlocking {
        val diagnosticDao = FakeDiagnosticLogDao()
        val telemetryDao = FakeTelemetryObservationDao()
        val aggregateDao = FakeDailyUsabilityAggregateDao()
        val manager = DataLifecycleManager(diagnosticDao, telemetryDao, aggregateDao)

        val now = 1_700_000_000_000L
        val oldTimestamp = now - 20L * 24 * 60 * 60 * 1000 // past the 14-day telemetry retention
        telemetryDao.rows.add(observation(1, oldTimestamp, labelStatus = "UNRESOLVED"))
        telemetryDao.rows.add(observation(2, oldTimestamp, labelStatus = "RESOLVED"))

        manager.runMaintenance(diagnosticRetentionDays = 30, telemetryRetentionDays = 14, nowMs = now)

        assertEquals(1, telemetryDao.rows.size)
        assertEquals(1L, telemetryDao.rows.first().id)
    }
}
