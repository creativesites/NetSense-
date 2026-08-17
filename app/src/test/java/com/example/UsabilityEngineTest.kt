package com.example

import com.example.engine.UsabilityEngine
import com.example.model.DiagnosticMode
import com.example.model.DiagnosticProbeResult
import com.example.model.EndpointProbeDetail
import com.example.model.NetworkClassification
import com.example.model.NetworkSnapshot
import com.example.model.NetworkTransport
import com.example.model.UsabilityRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsabilityEngineTest {

    @Test
    fun `test disconnected network returns 0 and unusable rating`() {
        val snapshot = NetworkSnapshot(isConnected = false, primaryTransport = NetworkTransport.NONE)
        val result = UsabilityEngine.calculateScore(snapshot, null)

        assertEquals(0, result.score)
        assertEquals(UsabilityRating.UNUSABLE, result.rating)
        assertFalse(result.isZombieConnection)
    }

    @Test
    fun `test zombie connection detected when radio signal is strong but probes fail`() {
        val snapshot = NetworkSnapshot(
            isConnected = true,
            isValidated = false,
            primaryTransport = NetworkTransport.CELLULAR,
            signalLevel = 4, // 4 bars
            carrierName = "Airtel"
        )
        val probe = DiagnosticProbeResult(
            mode = DiagnosticMode.STANDARD,
            primaryEndpoint = EndpointProbeDetail(
                endpointHost = "connectivitycheck.gstatic.com",
                dnsSuccess = false,
                tcpSuccess = false,
                httpSuccess = false
            ),
            packetLossPct = 1.0f
        )
        val result = UsabilityEngine.calculateScore(snapshot, probe)

        assertTrue(result.isZombieConnection)
        assertTrue(result.score < 35)
        assertEquals(NetworkClassification.RADIO_ONLY_NO_INTERNET, UsabilityEngine.classify(snapshot, result))
    }

    @Test
    fun `test healthy connection produces high score and optimal rating`() {
        val snapshot = NetworkSnapshot(
            isConnected = true,
            isValidated = true,
            primaryTransport = NetworkTransport.WIFI,
            signalLevel = 4
        )
        val probe = DiagnosticProbeResult(
            mode = DiagnosticMode.STANDARD,
            primaryEndpoint = EndpointProbeDetail(
                endpointHost = "connectivitycheck.gstatic.com",
                dnsLookupMs = 25L,
                dnsSuccess = true,
                tcpHandshakeMs = 45L,
                tcpSuccess = true,
                httpLatencyMs = 80L,
                httpSuccess = true,
                httpStatusCode = 204
            ),
            packetLossPct = 0f
        )
        val result = UsabilityEngine.calculateScore(snapshot, probe)

        assertTrue(result.score >= 85)
        assertEquals(UsabilityRating.OPTIMAL, result.rating)
        assertFalse(result.isZombieConnection)
        assertEquals(NetworkClassification.INTERNET_OPTIMAL, UsabilityEngine.classify(snapshot, result))
    }
}
