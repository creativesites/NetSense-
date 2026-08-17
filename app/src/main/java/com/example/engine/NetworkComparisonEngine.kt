package com.example.engine

import com.example.model.CellularRfSnapshot
import com.example.model.DiagnosticProbeResult
import com.example.model.NetworkComparisonMetric
import com.example.model.NetworkComparisonSummary
import com.example.model.NetworkSnapshot
import com.example.model.NetworkTransport
import com.example.model.WifiBand
import com.example.model.WifiRadarSnapshot
import kotlin.math.max
import kotlin.math.min

class NetworkComparisonEngine {

    fun generateComparison(
        snapshot: NetworkSnapshot,
        wifiRadar: WifiRadarSnapshot,
        cellularRf: CellularRfSnapshot,
        probe: DiagnosticProbeResult?,
        stabilityScore: Int,
        stabilityVerdict: String
    ): NetworkComparisonSummary {
        val isWifi = snapshot.primaryTransport == NetworkTransport.WIFI
        val isCellular = snapshot.primaryTransport == NetworkTransport.CELLULAR

        // Wi-Fi evaluation
        var wifiSubScore = if (wifiRadar.isWifiConnected) 80 else 30
        if (wifiRadar.isWifiConnected) {
            if (wifiRadar.rssiDbm > -65) wifiSubScore += 15
            else if (wifiRadar.rssiDbm < -78) wifiSubScore -= 20

            if (wifiRadar.band == WifiBand.BAND_5_GHZ || wifiRadar.band == WifiBand.BAND_6_GHZ) {
                wifiSubScore += 10
            }
            if (snapshot.isMetered) wifiSubScore -= 10
        }

        // Cellular evaluation
        var cellSubScore = if (cellularRf.isCellularConnected) 75 else 25
        if (cellularRf.isCellularConnected) {
            val rsrp = cellularRf.rsrpDbm ?: -88
            if (rsrp > -85) cellSubScore += 15
            else if (rsrp < -105) cellSubScore -= 25

            if (cellularRf.dataNetworkType.contains("5G")) {
                cellSubScore += 15
            }
            if (cellularRf.isRoaming) cellSubScore -= 10
        }

        if (probe != null && probe.packetLossPct > 0.2f) {
            if (isWifi) wifiSubScore -= 30
            if (isCellular) cellSubScore -= 30
        }

        wifiSubScore = max(10, min(100, wifiSubScore))
        cellSubScore = max(10, min(100, cellSubScore))

        val winner = when {
            wifiSubScore > cellSubScore + 8 -> NetworkTransport.WIFI
            cellSubScore > wifiSubScore + 8 -> NetworkTransport.CELLULAR
            else -> if (isWifi) NetworkTransport.WIFI else NetworkTransport.CELLULAR
        }

        val recTitle: String
        val recBody: String

        if (winner == NetworkTransport.WIFI) {
            recTitle = "Wi-Fi is the Recommended Path"
            recBody = if (wifiRadar.band == WifiBand.BAND_5_GHZ || wifiRadar.band == WifiBand.BAND_6_GHZ) {
                "Wi-Fi operates on a clean ${wifiRadar.band.label} channel with unmetered data bandwidth and lower overall latency."
            } else {
                "Wi-Fi link is performing better than mobile radio. Keep Wi-Fi active for bandwidth-heavy tasks."
            }
        } else if (winner == NetworkTransport.CELLULAR) {
            recTitle = "Cellular Outperforms Wi-Fi"
            recBody = "Cellular radio (${cellularRf.dataNetworkType}) provides superior packet stability. If Wi-Fi is stalling, toggle Wi-Fi off or enable Wi-Fi Assist."
        } else {
            recTitle = "Transports are Evenly Balanced"
            recBody = "Both Wi-Fi and Cellular are showing similar RF parameters and latency profiles."
        }

        val metrics = listOf(
            NetworkComparisonMetric(
                label = "Latency & Ping RTT",
                wifiValue = if (isWifi && probe?.averageTcpMs != null) "${probe.averageTcpMs} ms" else "24 ms (Est)",
                cellularValue = if (isCellular && probe?.averageTcpMs != null) "${probe.averageTcpMs} ms" else "38 ms (Est)",
                wifiScore = if (isWifi) 88 else 80,
                cellularScore = if (isCellular) 82 else 75,
                preferredTransport = NetworkTransport.WIFI
            ),
            NetworkComparisonMetric(
                label = "Signal & RF Strength",
                wifiValue = "${wifiRadar.rssiDbm} dBm (${wifiRadar.band.label})",
                cellularValue = "${cellularRf.rsrpDbm ?: -88} dBm (${cellularRf.dataNetworkType})",
                wifiScore = max(20, min(100, 2 * (wifiRadar.rssiDbm + 100))),
                cellularScore = max(20, min(100, 2 * ((cellularRf.rsrpDbm ?: -88) + 120))),
                preferredTransport = if (wifiRadar.rssiDbm > -70) NetworkTransport.WIFI else NetworkTransport.CELLULAR
            ),
            NetworkComparisonMetric(
                label = "Channel Width / Bandwidth",
                wifiValue = "${wifiRadar.channelWidthMhz} MHz (${wifiRadar.linkSpeedMbps} Mbps link)",
                cellularValue = if (cellularRf.dataNetworkType.contains("5G")) "100 MHz (5G Carrier Agg)" else "20 MHz (LTE)",
                wifiScore = if (wifiRadar.channelWidthMhz >= 80) 95 else 75,
                cellularScore = if (cellularRf.dataNetworkType.contains("5G")) 90 else 70,
                preferredTransport = NetworkTransport.WIFI
            ),
            NetworkComparisonMetric(
                label = "Data Billing & Metering",
                wifiValue = if (snapshot.isMetered && isWifi) "Metered Hotspot" else "Unmetered / Unlimited",
                cellularValue = "Metered Cellular Data",
                wifiScore = if (snapshot.isMetered && isWifi) 60 else 98,
                cellularScore = 65,
                preferredTransport = NetworkTransport.WIFI
            )
        )

        return NetworkComparisonSummary(
            wifiScore = wifiSubScore,
            cellularScore = cellSubScore,
            overallWinner = winner,
            recommendationTitle = recTitle,
            recommendationBody = recBody,
            metrics = metrics,
            stabilityScore = stabilityScore,
            stabilityVerdict = stabilityVerdict
        )
    }
}
