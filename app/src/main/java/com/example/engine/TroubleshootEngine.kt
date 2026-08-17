package com.example.engine

import com.example.model.DiagnosticProbeResult
import com.example.model.NetworkSnapshot
import com.example.model.NetworkTransport
import com.example.model.UsabilityScoreResult

enum class IssueSeverity {
    CRITICAL,
    HIGH,
    MODERATE,
    HEALTHY
}

enum class IssueCategory(val label: String) {
    ZOMBIE_LINK("Zombie Radio Link"),
    DNS_RESOLUTION("DNS & Name Resolution"),
    TCP_TRANSPORT("Transport & Packet Flow"),
    CAPTIVE_PORTAL("Captive Portal / Walled Garden"),
    RADIO_SIGNAL("Radio Signal & Coverage"),
    ROAMING_APN("APN & Carrier Routing"),
    BUFFERBLOAT("Queue Congestion & Jitter"),
    HEALTHY_SYSTEM("Healthy Connection")
}

data class GuidedRemediationStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val actionType: String? = null // e.g. "SETTINGS_INTERNET", "SETTINGS_AIRPLANE", "RETRY_PROBE"
)

data class TroubleshootFinding(
    val id: String,
    val title: String,
    val category: IssueCategory,
    val severity: IssueSeverity,
    val technicalExplanation: String,
    val likelyImpact: String,
    val steps: List<GuidedRemediationStep>
)

object TroubleshootEngine {

    fun analyze(
        snapshot: NetworkSnapshot,
        probe: DiagnosticProbeResult?,
        scoreResult: UsabilityScoreResult
    ): List<TroubleshootFinding> {
        val findings = mutableListOf<TroubleshootFinding>()

        // 1. Check for Zombie Connection
        if (scoreResult.isZombieConnection || (snapshot.isConnected && probe != null && !probe.overallDnsSuccess && !probe.overallTcpSuccess)) {
            findings.add(
                TroubleshootFinding(
                    id = "zombie_connection_detected",
                    title = "Active Radio Link with Dead Upstream Gateway",
                    category = IssueCategory.ZOMBIE_LINK,
                    severity = IssueSeverity.CRITICAL,
                    technicalExplanation = "Your device has a valid link-layer association (${snapshot.primaryTransport.name}, ${snapshot.signalDbm ?: -80} dBm), but outbound packets are dropped before reaching default upstream internet routes.",
                    likelyImpact = "All apps display 'No Internet connection' or endless spinners despite full signal bars.",
                    steps = listOf(
                        GuidedRemediationStep(1, "Toggle Airplane Mode", "Toggle Airplane mode for 5 seconds to force the modem to tear down stale PDP contexts and re-attach to the tower.", "SETTINGS_AIRPLANE"),
                        GuidedRemediationStep(2, "Switch Network Band", "Toggle between 4G/5G or reconnect to Wi-Fi to refresh link-layer routing tables.", "SETTINGS_INTERNET"),
                        GuidedRemediationStep(3, "Verify Mobile Data APN", "Check if carrier data roaming or APN access point settings are restricted on your carrier (${snapshot.carrierName ?: "Carrier"}).", "SETTINGS_INTERNET")
                    )
                )
            )
        }

        // 2. Check for DNS Resolution Failure / Private DNS Block
        if (probe != null && !probe.overallDnsSuccess && snapshot.isConnected) {
            findings.add(
                TroubleshootFinding(
                    id = "dns_resolution_failure",
                    title = "DNS Lookup Timeout / Private DNS Block",
                    category = IssueCategory.DNS_RESOLUTION,
                    severity = IssueSeverity.HIGH,
                    technicalExplanation = "System resolver failed to convert hostnames (clients3.google.com, cloudflare.com) to IP addresses. Often caused by strict Private DNS (DoT) servers being blocked by local firewalls.",
                    likelyImpact = "IP-based connections might work, but regular web pages and API services cannot connect.",
                    steps = listOf(
                        GuidedRemediationStep(1, "Check Android Private DNS", "Go to Network Settings > Private DNS. If set to a custom hostname (e.g. AdGuard or NextDNS), switch to 'Automatic' or 'Off' to test.", "SETTINGS_INTERNET"),
                        GuidedRemediationStep(2, "Test Cloudflare / Google Fallback", "Execute a Micro-Probe using alternate upstream DNS servers in the Diagnostics tab.", "RETRY_PROBE"),
                        GuidedRemediationStep(3, "Flush Local Socket Cache", "Toggle Wi-Fi or Mobile Data off and on to clear Android's local resolver cache.", "SETTINGS_INTERNET")
                    )
                )
            )
        }

        // 3. Check for Captive Portal / Walled Garden
        if (snapshot.isCaptivePortal || (probe?.primaryEndpoint?.httpStatusCode == 302 || probe?.primaryEndpoint?.httpStatusCode == 301)) {
            findings.add(
                TroubleshootFinding(
                    id = "captive_portal_detected",
                    title = "Captive Portal Login or Interception",
                    category = IssueCategory.CAPTIVE_PORTAL,
                    severity = IssueSeverity.HIGH,
                    technicalExplanation = "The network is intercepting HTTP 204 requests with status code ${probe?.primaryEndpoint?.httpStatusCode ?: 302} or returning an HTML splash screen. Internet access is gated.",
                    likelyImpact = "Internet traffic is blocked until you agree to Terms of Service or log into the network portal.",
                    steps = listOf(
                        GuidedRemediationStep(1, "Open Network Sign-In", "Tap the Android system notification to open the captive portal login prompt.", "SETTINGS_INTERNET"),
                        GuidedRemediationStep(2, "Open Browser Login Page", "Open Chrome or your browser and visit http://connectivitycheck.gstatic.com/generate_204 to trigger the gateway redirect.", null)
                    )
                )
            )
        }

        // 4. Check for Weak Radio Signal & Cell Boundary Flapping
        val signalLevel = snapshot.signalLevel ?: 3
        val signalDbm = snapshot.signalDbm ?: -85
        if (signalDbm < -110 || signalLevel <= 1) {
            findings.add(
                TroubleshootFinding(
                    id = "weak_radio_signal",
                    title = "Degraded Radio Link Quality",
                    category = IssueCategory.RADIO_SIGNAL,
                    severity = IssueSeverity.MODERATE,
                    technicalExplanation = "Raw RSSI/RSRP is $signalDbm dBm (Level $signalLevel/4). Low RF signal-to-noise ratio triggers high physical layer retransmissions and packet loss.",
                    likelyImpact = "High packet latency, intermittent stalling, and rapid battery drain due to modem power amplification.",
                    steps = listOf(
                        GuidedRemediationStep(1, "Move Toward Open Area", "Reposition away from concrete structures or metal shielding.", null),
                        GuidedRemediationStep(2, "Force LTE / 4G Only", "If 5G is on fringe coverage, lock preferred network mode to LTE for better stability.", "SETTINGS_INTERNET")
                    )
                )
            )
        }

        // 5. Check for High TCP Jitter / Bufferbloat
        val jitter = probe?.tcpJitterMs ?: 0L
        val tcpRtt = probe?.averageTcpMs ?: 0L
        if (jitter > 120L || (tcpRtt > 300L && probe?.overallTcpSuccess == true)) {
            findings.add(
                TroubleshootFinding(
                    id = "bufferbloat_jitter_detected",
                    title = "Bufferbloat & High Latency Jitter",
                    category = IssueCategory.BUFFERBLOAT,
                    severity = IssueSeverity.MODERATE,
                    technicalExplanation = "TCP handshake round-trip latency measured ${tcpRtt}ms with ±${jitter}ms jitter variance. Indicates upstream queue buffering or congested intermediate hops.",
                    likelyImpact = "Choppy VoIP audio, lagging video streams, and delayed interactive user experiences.",
                    steps = listOf(
                        GuidedRemediationStep(1, "Pause Background Syncs", "Verify if background app downloads, cloud backups, or torrents are saturating uplink buffers.", null),
                        GuidedRemediationStep(2, "Test Micro-Probe", "Run a low-bandwidth Micro-Probe to verify base unloaded round-trip time.", "RETRY_PROBE")
                    )
                )
            )
        }

        // 6. If completely offline
        if (!snapshot.isConnected) {
            findings.add(
                TroubleshootFinding(
                    id = "network_disconnected",
                    title = "All Network Radios Disconnected",
                    category = IssueCategory.RADIO_SIGNAL,
                    severity = IssueSeverity.CRITICAL,
                    technicalExplanation = "Android ConnectivityManager reports no active default network route (Wi-Fi and Cellular are both disconnected or in Airplane mode).",
                    likelyImpact = "No network communication possible.",
                    steps = listOf(
                        GuidedRemediationStep(1, "Enable Wi-Fi or Mobile Data", "Open Internet Settings to connect to an available Wi-Fi access point or turn on Cellular Data.", "SETTINGS_INTERNET"),
                        GuidedRemediationStep(2, "Disable Airplane Mode", "Ensure Airplane mode is turned off.", "SETTINGS_AIRPLANE")
                    )
                )
            )
        }

        // 7. If everything is healthy
        if (findings.isEmpty()) {
            findings.add(
                TroubleshootFinding(
                    id = "all_systems_optimal",
                    title = "Network Path Fully Verified & Healthy",
                    category = IssueCategory.HEALTHY_SYSTEM,
                    severity = IssueSeverity.HEALTHY,
                    technicalExplanation = "DNS resolution (${probe?.averageDnsMs ?: 0}ms), TCP handshake (${probe?.averageTcpMs ?: 0}ms), and HTTP 204 TTFB (${probe?.averageHttpMs ?: 0}ms) are within optimal thresholds with 0% packet loss.",
                    likelyImpact = "All network-dependent applications (streaming, gaming, voice, web) will perform reliably.",
                    steps = listOf(
                        GuidedRemediationStep(1, "Continuous Sentinel Monitoring", "Keep the background sentinel enabled to receive instant alerts if network health degrades.", null)
                    )
                )
            )
        }

        return findings
    }
}
