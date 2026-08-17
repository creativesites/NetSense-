package com.example.model

enum class WifiBand(val label: String, val frequencyRange: String, val optimalFor: String) {
    BAND_2_4_GHZ("2.4 GHz", "2412 - 2484 MHz", "Long Range & Wall Penetration"),
    BAND_5_GHZ("5 GHz", "5170 - 5835 MHz", "High Throughput & Low Interference"),
    BAND_6_GHZ("6 GHz (Wi-Fi 6E/7)", "5925 - 7125 MHz", "Ultra-Wide Channels & Ultra-Low Latency"),
    UNKNOWN("Unknown Band", "N/A", "N/A")
}

data class WifiRadarSnapshot(
    val isWifiConnected: Boolean = false,
    val ssid: String? = null,
    val bssid: String? = null,
    val frequencyMhz: Int = 0,
    val band: WifiBand = WifiBand.UNKNOWN,
    val channelNumber: Int = 0,
    val channelWidthMhz: Int = 20, // 20, 40, 80, 160
    val rssiDbm: Int = -100, // e.g. -55 dBm
    val linkSpeedMbps: Int = 0,
    val txLinkSpeedMbps: Int = 0,
    val rxLinkSpeedMbps: Int = 0,
    val wifiStandard: String = "Wi-Fi 5 / 6", // e.g. Wi-Fi 6 (802.11ax)
    val gatewayIp: String? = null,
    val subnetMask: String? = null,
    val signalStrengthPercent: Int = 0,
    val congestionLevel: String = "Low", // Low, Moderate, High
    val interferenceRisk: String = "Minimal"
)

data class CellularRfSnapshot(
    val isCellularConnected: Boolean = false,
    val carrierName: String? = null,
    val dataNetworkType: String = "Unknown",
    val rsrpDbm: Int? = null, // Reference Signal Received Power (-140..-44 dBm)
    val rsrqDb: Int? = null,  // Reference Signal Received Quality (-20..-3 dB)
    val sinrDb: Int? = null,  // Signal-to-Interference-plus-Noise Ratio (-10..30 dB)
    val cqi: Int? = null,     // Channel Quality Indicator (1..15)
    val cellId: String? = null,
    val pci: Int? = null,     // Physical Cell ID
    val tac: Int? = null,     // Tracking Area Code
    val bandIndicator: String? = null, // e.g. "LTE Band 7 (2600 MHz)" / "5G n78"
    val isRoaming: Boolean = false,
    val isCarrierAggregationActive: Boolean = false,
    val simState: String = "Ready"
)

data class NetworkComparisonMetric(
    val label: String,
    val wifiValue: String,
    val cellularValue: String,
    val wifiScore: Int, // 0..100
    val cellularScore: Int, // 0..100
    val preferredTransport: NetworkTransport
)

data class NetworkComparisonSummary(
    val wifiScore: Int = 0,
    val cellularScore: Int = 0,
    val overallWinner: NetworkTransport = NetworkTransport.NONE,
    val recommendationTitle: String = "Analyzing Network Paths...",
    val recommendationBody: String = "Evaluating Wi-Fi vs Cellular telemetry and probe responses.",
    val metrics: List<NetworkComparisonMetric> = emptyList(),
    val stabilityScore: Int = 85, // 0..100
    val stabilityVerdict: String = "Stable Link"
)
