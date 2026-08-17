package com.netsense.netpulse.engine

import com.netsense.netpulse.model.DiagnosticMode
import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.EndpointProbeDetail
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport

enum class SimulatedFaultScenario(
    val title: String,
    val description: String,
    val simulatedRating: String
) {
    NONE(
        title = "Live Network (Normal)",
        description = "Uses actual device telemetry and live network probes.",
        simulatedRating = "LIVE"
    ),
    ZOMBIE_RADIO(
        title = "Zombie Connection (Full Signal, No Data)",
        description = "Simulates strong LTE signal (-72 dBm) with 100% upstream packet loss and dead DNS.",
        simulatedRating = "UNUSABLE"
    ),
    DNS_BLACKHOLE(
        title = "Private DNS / DoT Blackhole",
        description = "Simulates active TCP routes but failing DNS resolution (NXDOMAIN / timeout).",
        simulatedRating = "POOR"
    ),
    CAPTIVE_PORTAL_GATED(
        title = "Captive Portal Gated (HTTP 302)",
        description = "Simulates hotel/airport Wi-Fi gateway returning HTTP 302 login redirect.",
        simulatedRating = "UNUSABLE"
    ),
    HIGH_BUFFERBLOAT(
        title = "Severe Bufferbloat & Jitter",
        description = "Simulates congested uplink with 450ms TCP latency and ±180ms jitter.",
        simulatedRating = "DEGRADED"
    )
}

object FaultSimulator {

    fun generateSimulatedSnapshot(scenario: SimulatedFaultScenario, realSnapshot: NetworkSnapshot): NetworkSnapshot {
        return when (scenario) {
            SimulatedFaultScenario.NONE -> realSnapshot
            SimulatedFaultScenario.ZOMBIE_RADIO -> realSnapshot.copy(
                isConnected = true,
                primaryTransport = NetworkTransport.CELLULAR,
                carrierName = realSnapshot.carrierName ?: "Simulated Carrier LTE",
                cellularDataNetworkType = "5G",
                signalLevel = 4,
                signalDbm = -72,
                isMetered = true
            )
            SimulatedFaultScenario.DNS_BLACKHOLE -> realSnapshot.copy(
                isConnected = true,
                primaryTransport = NetworkTransport.WIFI,
                signalLevel = 4,
                signalDbm = -55
            )
            SimulatedFaultScenario.CAPTIVE_PORTAL_GATED -> realSnapshot.copy(
                isConnected = true,
                isCaptivePortal = true,
                primaryTransport = NetworkTransport.WIFI,
                signalLevel = 4,
                signalDbm = -58
            )
            SimulatedFaultScenario.HIGH_BUFFERBLOAT -> realSnapshot.copy(
                isConnected = true,
                primaryTransport = NetworkTransport.WIFI,
                signalLevel = 3,
                signalDbm = -78
            )
        }
    }

    fun generateSimulatedProbe(scenario: SimulatedFaultScenario, realProbe: DiagnosticProbeResult?): DiagnosticProbeResult? {
        return when (scenario) {
            SimulatedFaultScenario.NONE -> realProbe
            SimulatedFaultScenario.ZOMBIE_RADIO -> DiagnosticProbeResult(
                mode = DiagnosticMode.STANDARD,
                primaryEndpoint = EndpointProbeDetail(
                    endpointHost = "clients3.google.com",
                    dnsSuccess = false,
                    dnsLookupMs = null,
                    tcpSuccess = false,
                    tcpHandshakeMs = null,
                    httpSuccess = false,
                    httpStatusCode = null,
                    httpLatencyMs = null,
                    errorMessage = "ConnectException: Connection timed out"
                ),
                secondaryEndpoint = EndpointProbeDetail(
                    endpointHost = "1.1.1.1",
                    dnsSuccess = false,
                    dnsLookupMs = null,
                    tcpSuccess = false,
                    tcpHandshakeMs = null,
                    httpSuccess = false,
                    httpStatusCode = null,
                    httpLatencyMs = null,
                    errorMessage = "SocketTimeoutException: Upstream route unreachable"
                ),
                packetLossPct = 1.0f,
                totalProbesSent = 6,
                totalProbesReceived = 0,
                durationMs = 4200L
            )
            SimulatedFaultScenario.DNS_BLACKHOLE -> DiagnosticProbeResult(
                mode = DiagnosticMode.STANDARD,
                primaryEndpoint = EndpointProbeDetail(
                    endpointHost = "clients3.google.com",
                    dnsSuccess = false,
                    dnsLookupMs = null,
                    tcpSuccess = true,
                    tcpHandshakeMs = 38L,
                    httpSuccess = false,
                    httpStatusCode = null,
                    httpLatencyMs = null,
                    errorMessage = "UnknownHostException: Unable to resolve host"
                ),
                secondaryEndpoint = EndpointProbeDetail(
                    endpointHost = "1.1.1.1",
                    dnsSuccess = false,
                    dnsLookupMs = null,
                    tcpSuccess = true,
                    tcpHandshakeMs = 35L,
                    httpSuccess = false,
                    httpStatusCode = null,
                    httpLatencyMs = null,
                    errorMessage = "UnknownHostException: Private DNS timeout"
                ),
                packetLossPct = 0.5f,
                totalProbesSent = 6,
                totalProbesReceived = 3,
                durationMs = 2800L
            )
            SimulatedFaultScenario.CAPTIVE_PORTAL_GATED -> DiagnosticProbeResult(
                mode = DiagnosticMode.STANDARD,
                primaryEndpoint = EndpointProbeDetail(
                    endpointHost = "connectivitycheck.gstatic.com",
                    dnsSuccess = true,
                    dnsLookupMs = 22L,
                    tcpSuccess = true,
                    tcpHandshakeMs = 30L,
                    httpSuccess = false,
                    httpStatusCode = 302,
                    httpLatencyMs = 45L,
                    errorMessage = "HTTP 302 Found (Redirect to splash portal)"
                ),
                secondaryEndpoint = null,
                packetLossPct = 0.0f,
                totalProbesSent = 3,
                totalProbesReceived = 3,
                durationMs = 97L
            )
            SimulatedFaultScenario.HIGH_BUFFERBLOAT -> DiagnosticProbeResult(
                mode = DiagnosticMode.DEEP,
                primaryEndpoint = EndpointProbeDetail(
                    endpointHost = "clients3.google.com",
                    dnsSuccess = true,
                    dnsLookupMs = 85L,
                    tcpSuccess = true,
                    tcpHandshakeMs = 460L,
                    httpSuccess = true,
                    httpStatusCode = 204,
                    httpLatencyMs = 520L,
                    errorMessage = null
                ),
                secondaryEndpoint = EndpointProbeDetail(
                    endpointHost = "1.1.1.1",
                    dnsSuccess = true,
                    dnsLookupMs = 90L,
                    tcpSuccess = true,
                    tcpHandshakeMs = 440L,
                    httpSuccess = true,
                    httpStatusCode = 200,
                    httpLatencyMs = 510L,
                    errorMessage = null
                ),
                tcpJitterMs = 185L,
                packetLossPct = 0.16f,
                totalProbesSent = 6,
                totalProbesReceived = 5,
                durationMs = 1655L
            )
        }
    }
}
