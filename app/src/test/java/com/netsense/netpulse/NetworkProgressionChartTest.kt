package com.netsense.netpulse

import com.netsense.netpulse.data.DiagnosticLogEntity
import com.netsense.netpulse.ui.ChartPeriod
import com.netsense.netpulse.ui.buildChartPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkProgressionChartTest {

    private fun log(timestamp: Long, score: Int) = DiagnosticLogEntity(
        timestamp = timestamp,
        score = score,
        rating = "GOOD",
        classification = "INTERNET_VALIDATED",
        isZombieConnection = false,
        primaryDiagnosis = "test",
        rootCauseSummary = "test",
        transport = "WIFI",
        carrierName = null,
        cellularNetworkType = null,
        signalLevel = null,
        signalDbm = null,
        dnsLatencyMs = null,
        dnsSuccess = true,
        tcpHandshakeMs = null,
        tcpSuccess = true,
        tcpJitterMs = null,
        httpLatencyMs = null,
        httpSuccess = true,
        httpStatusCode = null,
        packetLossPct = 0f,
        durationMs = 0,
        probeMode = "MICRO"
    )

    @Test
    fun `empty log history produces no points`() {
        val points = buildChartPoints(emptyList(), ChartPeriod.TODAY, nowMs = 100_000L)
        assertTrue(points.isEmpty())
    }

    @Test
    fun `logs outside the window are excluded, never fabricated into the visible range`() {
        val now = 10_000_000L
        val logs = listOf(
            log(timestamp = now - ChartPeriod.TODAY.windowMs - 60_000L, score = 10), // just outside
            log(timestamp = now - 1_000L, score = 90) // inside
        )
        val points = buildChartPoints(logs, ChartPeriod.TODAY, nowMs = now)
        assertEquals(1, points.size)
        assertEquals(90f, points.first().averageScore, 0.01f)
    }

    @Test
    fun `samples in the same bucket are averaged, not double-counted as separate points`() {
        val now = 10_000_000L
        val bucketMs = ChartPeriod.TODAY.bucketMs
        val windowStart = now - ChartPeriod.TODAY.windowMs
        val bucketStart = windowStart + bucketMs // second bucket, safely inside the window
        val logs = listOf(
            log(timestamp = bucketStart + 1_000L, score = 60),
            log(timestamp = bucketStart + 2_000L, score = 80)
        )
        val points = buildChartPoints(logs, ChartPeriod.TODAY, nowMs = now)
        assertEquals(1, points.size)
        assertEquals(70f, points.first().averageScore, 0.01f)
        assertEquals(2, points.first().sampleCount)
    }

    @Test
    fun `a bucket with no samples is simply absent, never interpolated or zero-filled`() {
        val now = 10_000_000L
        val bucketMs = ChartPeriod.TODAY.bucketMs
        val windowStart = now - ChartPeriod.TODAY.windowMs
        // Bucket 0 has data, bucket 1 has none, bucket 2 has data.
        val logs = listOf(
            log(timestamp = windowStart + 1_000L, score = 40),
            log(timestamp = windowStart + 2 * bucketMs + 1_000L, score = 90)
        )
        val points = buildChartPoints(logs, ChartPeriod.TODAY, nowMs = now)
        assertEquals(2, points.size) // not 3 - the empty middle bucket contributes nothing
        assertEquals(40f, points[0].averageScore, 0.01f)
        assertEquals(90f, points[1].averageScore, 0.01f)
    }

    @Test
    fun `points are returned in chronological order`() {
        val now = 10_000_000L
        val bucketMs = ChartPeriod.WEEK.bucketMs
        val windowStart = now - ChartPeriod.WEEK.windowMs
        val logs = listOf(
            log(timestamp = windowStart + 3 * bucketMs + 1_000L, score = 30),
            log(timestamp = windowStart + 1_000L, score = 10),
            log(timestamp = windowStart + bucketMs + 1_000L, score = 20)
        )
        val points = buildChartPoints(logs, ChartPeriod.WEEK, nowMs = now)
        assertEquals(listOf(10f, 20f, 30f), points.map { it.averageScore })
    }
}
