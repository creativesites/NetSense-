package com.netsense.netpulse.model

enum class NetworkTransport {
    CELLULAR,
    WIFI,
    ETHERNET,
    BLUETOOTH,
    VPN,
    OTHER,
    NONE
}

enum class DiagnosticMode(val title: String, val description: String, val estimatedPayloadKb: Float) {
    MICRO("Micro Probe", "Ultra-fast DNS & TCP SYN check (~0.8 KB)", 0.8f),
    STANDARD("Standard Probe", "DNS + TCP RTT + HTTP 204 validation (~2.2 KB)", 2.2f),
    DEEP("Deep Diagnostic", "Multi-endpoint verification, TCP Jitter & loss (~4.5 KB)", 4.5f)
}

enum class NetworkClassification(val label: String, val shortDescription: String) {
    NO_NETWORK(
        "No Connection",
        "Radio is disconnected or device is offline."
    ),
    RADIO_ONLY_NO_INTERNET(
        "Zombie Connection",
        "Cellular link active with strong signal, but upstream Internet routing is dead."
    ),
    INTERNET_UNVALIDATED(
        "Unvalidated Internet",
        "Network capability is unverified by platform; captive portal may be present."
    ),
    INTERNET_DEGRADED(
        "Degraded Usability",
        "Connection is active but suffering from elevated latency, jitter, or packet loss."
    ),
    INTERNET_VALIDATED(
        "Internet Validated",
        "Platform & probe have confirmed basic Internet reachability."
    ),
    INTERNET_OPTIMAL(
        "Optimal Connection",
        "High-performance connection with fast DNS, minimal RTT, and zero loss."
    )
}

enum class UsabilityRating(val label: String, val minScore: Int, val maxScore: Int) {
    OPTIMAL("Optimal", 85, 100),
    GOOD("Good", 60, 84),
    DEGRADED("Degraded", 35, 59),
    POOR("Poor", 10, 34),
    UNUSABLE("Unusable", 0, 9);

    companion object {
        fun fromScore(score: Int): UsabilityRating = when {
            score >= 85 -> OPTIMAL
            score >= 60 -> GOOD
            score >= 35 -> DEGRADED
            score >= 10 -> POOR
            else -> UNUSABLE
        }
    }
}

data class EndpointProbeDetail(
    val endpointHost: String,
    val ipAddress: String? = null,
    val dnsLookupMs: Long? = null,
    val dnsSuccess: Boolean = false,
    val tcpHandshakeMs: Long? = null,
    val tcpSuccess: Boolean = false,
    val httpStatusCode: Int? = null,
    val httpLatencyMs: Long? = null,
    val httpSuccess: Boolean = false,
    val errorMessage: String? = null
)

data class DiagnosticProbeResult(
    val timestamp: Long = System.currentTimeMillis(),
    val isRunning: Boolean = false,
    val mode: DiagnosticMode = DiagnosticMode.STANDARD,
    val primaryEndpoint: EndpointProbeDetail = EndpointProbeDetail("connectivitycheck.gstatic.com"),
    val secondaryEndpoint: EndpointProbeDetail? = null,
    val tcpJitterMs: Long? = null,
    val packetLossPct: Float = 0f,
    val totalProbesSent: Int = 0,
    val totalProbesReceived: Int = 0,
    val failureReason: String? = null,
    val durationMs: Long = 0
) {
    val overallDnsSuccess: Boolean get() = primaryEndpoint.dnsSuccess || (secondaryEndpoint?.dnsSuccess == true)
    val overallTcpSuccess: Boolean get() = primaryEndpoint.tcpSuccess || (secondaryEndpoint?.tcpSuccess == true)
    val overallHttpSuccess: Boolean get() = primaryEndpoint.httpSuccess || (secondaryEndpoint?.httpSuccess == true)
    val averageDnsMs: Long? get() = primaryEndpoint.dnsLookupMs ?: secondaryEndpoint?.dnsLookupMs
    val averageTcpMs: Long? get() = primaryEndpoint.tcpHandshakeMs ?: secondaryEndpoint?.tcpHandshakeMs
    val averageHttpMs: Long? get() = primaryEndpoint.httpLatencyMs ?: secondaryEndpoint?.httpLatencyMs
}

data class UsabilityScoreResult(
    val score: Int,
    val rating: UsabilityRating,
    val primaryDiagnosis: String,
    val rootCauseSummary: String,
    val explanatoryReasons: List<String>,
    val isZombieConnection: Boolean,
    val scoreBreakdown: Map<String, Int>
)

data class NetworkSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val isCaptivePortal: Boolean = false,
    val isMetered: Boolean = true,
    val primaryTransport: NetworkTransport = NetworkTransport.NONE,
    val activeTransports: Set<NetworkTransport> = emptySet(),
    val downstreamBandwidthKbps: Int = 0,
    val upstreamBandwidthKbps: Int = 0,
    val interfaceName: String? = null,
    val dnsServers: List<String> = emptyList(),
    val domains: String? = null,
    val carrierName: String? = null,
    val cellularDataNetworkType: String? = null,
    val signalLevel: Int? = null, // 0..4
    val signalDbm: Int? = null,
    val isAirplaneModeOn: Boolean = false,
    val classification: NetworkClassification = NetworkClassification.NO_NETWORK
)
