package com.example

import com.example.analytics.AnalyticsEngine
import com.example.data.DiagnosticLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3AnalyticsTest {

    @Test
    fun testAnalyticsEngine_calculatesAveragesAndZombiesAccurately() {
        val sampleLogs = listOf(
            DiagnosticLogEntity(
                id = 1,
                timestamp = System.currentTimeMillis() - 10000,
                score = 80,
                rating = "GOOD",
                classification = "USABLE_GOOD",
                isZombieConnection = false,
                primaryDiagnosis = "Internet is fully functional",
                rootCauseSummary = "Healthy upstream link",
                transport = "CELLULAR",
                carrierName = "Airtel Zambia",
                cellularNetworkType = "LTE",
                signalLevel = 4,
                signalDbm = -75,
                dnsLatencyMs = 25,
                dnsSuccess = true,
                tcpHandshakeMs = 45,
                tcpSuccess = true,
                tcpJitterMs = 5,
                httpLatencyMs = 60,
                httpSuccess = true,
                httpStatusCode = 204,
                packetLossPct = 0.0f,
                durationMs = 130,
                probeMode = "STANDARD"
            ),
            DiagnosticLogEntity(
                id = 2,
                timestamp = System.currentTimeMillis(),
                score = 10,
                rating = "UNUSABLE",
                classification = "ZOMBIE_CONNECTION",
                isZombieConnection = true,
                primaryDiagnosis = "Zombie Connection: Radio signal active but upstream dead",
                rootCauseSummary = "DNS lookup timeout",
                transport = "CELLULAR",
                carrierName = "MTN Zambia",
                cellularNetworkType = "LTE",
                signalLevel = 4,
                signalDbm = -70,
                dnsLatencyMs = null,
                dnsSuccess = false,
                tcpHandshakeMs = null,
                tcpSuccess = false,
                tcpJitterMs = null,
                httpLatencyMs = null,
                httpSuccess = false,
                httpStatusCode = null,
                packetLossPct = 1.0f,
                durationMs = 3500,
                probeMode = "MICRO"
            )
        )

        val analytics = AnalyticsEngine.generateAnalytics(sampleLogs)

        assertEquals(2, analytics.totalProbes)
        assertEquals(45, analytics.averageScore) // (80 + 10) / 2
        assertEquals(1, analytics.zombieCount)
        assertEquals(50f, analytics.uptimePercentage, 0.1f) // 1 out of 2 usable
        assertEquals(25L, analytics.averageDnsMs)
        assertEquals(45L, analytics.averageTcpMs)
        assertEquals(60L, analytics.averageHttpMs)
        assertEquals(1, analytics.zombieIncidents.size)
        assertTrue(analytics.zombieIncidents[0].triggerReason.contains("Zombie Connection"))
    }
}
