package com.example.engine

import android.os.Build
import com.example.model.CellularRfSnapshot
import com.example.model.DiagnosticProbeResult
import com.example.model.DualStackResult
import com.example.model.HopTraceResult
import com.example.model.IncidentReport
import com.example.model.NetworkSnapshot
import com.example.model.NetworkTransport
import com.example.model.PingTargetResult
import com.example.model.UsabilityScoreResult
import com.example.model.WifiRadarSnapshot
import com.example.telephony.TelephonySnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class IncidentReportEngine {

    fun generateTechnicalReport(
        snapshot: NetworkSnapshot,
        telephony: TelephonySnapshot,
        scoreResult: UsabilityScoreResult,
        probe: DiagnosticProbeResult?,
        wifiRadar: WifiRadarSnapshot,
        cellularRf: CellularRfSnapshot,
        pingResults: List<PingTargetResult>,
        dualStack: DualStackResult?,
        hopTrace: HopTraceResult?
    ): IncidentReport {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val timestampStr = dateFormat.format(Date())
        val reportId = "NP-" + UUID.randomUUID().toString().substring(0, 8).uppercase()

        val activeTransportName = when (snapshot.primaryTransport) {
            NetworkTransport.WIFI -> "Wi-Fi (${wifiRadar.ssid ?: "Unknown SSID"})"
            NetworkTransport.CELLULAR -> "Cellular (${telephony.carrierName ?: "Mobile Radio"})"
            NetworkTransport.ETHERNET -> "Ethernet Link"
            NetworkTransport.VPN -> "Encrypted VPN Tunnel"
            else -> "Offline / Disconnected"
        }

        val carrierOrSsid = if (snapshot.primaryTransport == NetworkTransport.WIFI) {
            wifiRadar.ssid ?: "Wi-Fi Network"
        } else {
            telephony.carrierName ?: "Cellular Carrier"
        }

        val md = buildString {
            appendLine("# 🌐 NetPulse Technical Diagnostic & Incident Audit Report")
            appendLine("**Report ID:** `$reportId`  |  **Generated:** $timestampStr")
            appendLine()
            appendLine("---")
            appendLine("### 📊 Executive Usability Summary")
            appendLine("- **Usability Health Score:** **${scoreResult.score}/100** (`${scoreResult.rating.label}`)")
            appendLine("- **Classification:** **${snapshot.classification.label}**")
            appendLine("- **Primary Diagnosis:** ${scoreResult.primaryDiagnosis}")
            appendLine("- **Root-Cause Analysis:** ${scoreResult.rootCauseSummary}")
            appendLine("- **Active Transport:** $activeTransportName")
            appendLine("- **Zombie Link Detected:** ${if (scoreResult.isZombieConnection) "⚠️ YES (Baseband Up, Gateway Dead)" else "No"}")
            appendLine("- **Captive Portal Gated:** ${if (snapshot.isCaptivePortal) "⚠️ YES (HTTP 302 Intercept)" else "No"}")
            appendLine()
            appendLine("---")
            appendLine("### 📱 Device & Physical Layer Telemetry")
            appendLine("- **Device Hardware:** ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android API ${Build.VERSION.SDK_INT})")
            appendLine("- **Network Interface:** `${snapshot.interfaceName ?: "wlan0/rmnet0"}`")
            appendLine("- **Signal Level:** ${snapshot.signalLevel ?: 0}/4 (${snapshot.signalDbm ?: -100} dBm)")
            appendLine("- **Data Metered:** ${if (snapshot.isMetered) "Yes (Cellular/Hotspot policy active)" else "No (Unmetered broadband)"}")
            appendLine("- **Assigned DNS Servers:** ${if (snapshot.dnsServers.isNotEmpty()) snapshot.dnsServers.joinToString(", ") else "None (Unassigned)"}")

            if (wifiRadar.isWifiConnected) {
                appendLine()
                appendLine("#### 📶 Wi-Fi RF Parameters")
                appendLine("- **SSID / BSSID:** `${wifiRadar.ssid}` / `${wifiRadar.bssid ?: "Hidden"}`")
                appendLine("- **Frequency Band:** ${wifiRadar.band.label} (${wifiRadar.frequencyMhz} MHz, Channel ${wifiRadar.channelNumber})")
                appendLine("- **Channel Width / Standard:** ${wifiRadar.channelWidthMhz} MHz / ${wifiRadar.wifiStandard}")
                appendLine("- **RSSI Attenuation:** ${wifiRadar.rssiDbm} dBm (Strength: ${wifiRadar.signalStrengthPercent}%)")
                appendLine("- **Link Speed:** ${wifiRadar.linkSpeedMbps} Mbps (Tx: ${wifiRadar.txLinkSpeedMbps} Mbps, Rx: ${wifiRadar.rxLinkSpeedMbps} Mbps)")
            }

            if (cellularRf.isCellularConnected) {
                appendLine()
                appendLine("#### 📡 Cellular Baseband & RF Parameters")
                appendLine("- **Carrier / Network Type:** ${cellularRf.carrierName} (${cellularRf.dataNetworkType})")
                appendLine("- **Serving Band:** ${cellularRf.bandIndicator ?: "LTE/5G Sub-6"}")
                appendLine("- **RSRP / RSRQ / SINR:** ${cellularRf.rsrpDbm ?: "N/A"} dBm / ${cellularRf.rsrqDb ?: "N/A"} dB / ${cellularRf.sinrDb ?: "N/A"} dB")
                appendLine("- **CQI / Cell ID:** CQI ${cellularRf.cqi ?: "N/A"} / CellID `${cellularRf.cellId ?: "N/A"}` (PCI: ${cellularRf.pci ?: "N/A"})")
                appendLine("- **Roaming / Carrier Aggregation:** Roaming: ${cellularRf.isRoaming} / CA: ${cellularRf.isCarrierAggregationActive}")
            }

            appendLine()
            appendLine("---")
            appendLine("### ⚡ Layer 4 & Layer 7 Active Probe Telemetry")
            if (probe != null) {
                appendLine("- **Probe Mode:** ${probe.mode.title} (Duration: ${probe.durationMs} ms)")
                appendLine("- **Packet Loss Rate:** ${(probe.packetLossPct * 100).toInt()}% (${probe.totalProbesReceived}/${probe.totalProbesSent} packets received)")
                appendLine("- **TCP Jitter Variance:** ${probe.tcpJitterMs ?: 0} ms")
                appendLine("- **DNS Resolution TTFB:** ${probe.averageDnsMs ?: "Failed"} ms (${if (probe.overallDnsSuccess) "RESOLVED" else "FAILED"})")
                appendLine("- **TCP SYN Handshake RTT:** ${probe.averageTcpMs ?: "Failed"} ms (${if (probe.overallTcpSuccess) "CONNECTED" else "TIMEOUT"})")
                appendLine("- **HTTP 204 TTFB Response:** ${probe.averageHttpMs ?: "Failed"} ms (${if (probe.overallHttpSuccess) "VALIDATED (HTTP 204)" else "FAILED"})")
            } else {
                appendLine("- *No active probe executed yet.*")
            }

            if (dualStack != null) {
                appendLine()
                appendLine("#### 🌐 Dual-Stack (IPv4 vs IPv6) Status")
                appendLine("- **Routing Status:** ${dualStack.status.title}")
                appendLine("- **IPv4 Reachable:** ${dualStack.ipv4Reachable} (RTT: ${dualStack.ipv4RttMs ?: "N/A"} ms) - IP: `${dualStack.ipv4Address ?: "N/A"}`")
                appendLine("- **IPv6 Reachable:** ${dualStack.ipv6Reachable} (RTT: ${dualStack.ipv6RttMs ?: "N/A"} ms) - IP: `${dualStack.ipv6Address ?: "None"}`")
                if (dualStack.happyEyeballsPenaltyMs > 0) {
                    appendLine("- **Happy Eyeballs Fallback Penalty:** ⚠️ +${dualStack.happyEyeballsPenaltyMs} ms delay")
                }
            }

            if (pingResults.isNotEmpty()) {
                appendLine()
                appendLine("---")
                appendLine("### 🎯 Multi-Host Reachability Matrix")
                appendLine("| Target Host | Category | RTT (ms) | TLS (ms) | Loss (%) | Status |")
                appendLine("| :--- | :--- | :--- | :--- | :--- | :--- |")
                pingResults.forEach { r ->
                    val rtt = r.rttMs?.toString() ?: "N/A"
                    val tls = r.tlsHandshakeMs?.toString() ?: "N/A"
                    val loss = (r.packetLossPct * 100).toInt().toString()
                    appendLine("| `${r.target.name}` | ${r.target.category.title} | $rtt | $tls | $loss% | ${r.statusMessage} |")
                }
            }

            if (hopTrace != null && hopTrace.hops.isNotEmpty()) {
                appendLine()
                appendLine("---")
                appendLine("### 📍 Path Hop Traceroute Analysis")
                appendLine("**Destination Target:** `${hopTrace.targetHost}`  |  **Total Path RTT:** ${hopTrace.totalPathLatencyMs} ms")
                appendLine()
                hopTrace.hops.forEach { hop ->
                    val rttStr = hop.rttMs?.let { "$it ms" } ?: "Timeout (*)"
                    appendLine("${hop.hopIndex}. **[${hop.nodeType.label}]** `${hop.ipOrHost}` — **$rttStr** (${hop.statusNote})")
                }
            }

            appendLine()
            appendLine("---")
            appendLine("### 🛠️ Recommended Corrective Actions")
            if (scoreResult.explanatoryReasons.isNotEmpty()) {
                scoreResult.explanatoryReasons.forEach { reason ->
                    appendLine("- $reason")
                }
            } else {
                appendLine("- Network is performing within optimal latency and throughput parameters.")
            }
            appendLine()
            appendLine("*Generated automatically by NetPulse Optimizer Engine.*")
        }

        val plainText = md.replace("#", "")
            .replace("**", "")
            .replace("`", "")
            .replace("|", " ")
            .replace("---", "--------------------------------------------------")

        return IncidentReport(
            reportId = reportId,
            timestampFormatted = timestampStr,
            overallScore = scoreResult.score,
            ratingLabel = scoreResult.rating.label,
            primaryIssue = scoreResult.primaryDiagnosis,
            transportUsed = activeTransportName,
            carrierOrSsid = carrierOrSsid,
            markdownContent = md,
            plainTextContent = plainText
        )
    }
}
