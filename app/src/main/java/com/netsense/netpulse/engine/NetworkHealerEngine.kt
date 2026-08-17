package com.netsense.netpulse.engine

import com.netsense.netpulse.model.DiagnosticProbeResult
import com.netsense.netpulse.model.DualStackResult
import com.netsense.netpulse.model.DualStackStatus
import com.netsense.netpulse.model.HealerActionItem
import com.netsense.netpulse.model.HealerActionType
import com.netsense.netpulse.model.NetworkClassification
import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.model.PingTargetResult
import com.netsense.netpulse.model.UsabilityScoreResult

class NetworkHealerEngine {

    fun generateHealingPlan(
        snapshot: NetworkSnapshot,
        scoreResult: UsabilityScoreResult,
        probe: DiagnosticProbeResult?,
        pingResults: List<PingTargetResult>,
        dualStack: DualStackResult?
    ): List<HealerActionItem> {
        val actions = mutableListOf<HealerActionItem>()

        // 1. Zombie Radio / Cellular Dead Link
        if (scoreResult.isZombieConnection || snapshot.classification == NetworkClassification.RADIO_ONLY_NO_INTERNET) {
            actions.add(
                HealerActionItem(
                    id = "healer_airplane_cycle",
                    title = "Radio PDP Context Reset",
                    description = "Cellular signal is strong but upstream routing is dead. Toggle Airplane Mode for 5 seconds to force the baseband to renegotiate a fresh PDP IP context.",
                    impactLevel = "High Impact",
                    actionType = HealerActionType.AIRPLANE_CYCLE,
                    isAutoFixable = false,
                    executionNote = "Opens Airplane Mode settings to re-bind baseband bearer."
                )
            )
        }

        // 2. Captive Portal Interception
        if (snapshot.isCaptivePortal) {
            actions.add(
                HealerActionItem(
                    id = "healer_captive_portal",
                    title = "Launch Captive Portal Gateway Login",
                    description = "Wi-Fi is intercepting upstream packets and redirecting HTTP to splash authentication portal. Open portal page to complete sign-in.",
                    impactLevel = "High Impact",
                    actionType = HealerActionType.OPEN_CAPTIVE_PORTAL,
                    isAutoFixable = true,
                    executionNote = "Triggers platform portal authorization browser."
                )
            )
        }

        // 3. DNS Optimization Recommendation based on Ping Matrix
        val fastestDns = pingResults
            .filter { it.target.port == 53 && it.isSuccess && it.rttMs != null }
            .minByOrNull { it.rttMs ?: 9999L }

        if (fastestDns != null && fastestDns.rttMs != null && fastestDns.rttMs < 35) {
            actions.add(
                HealerActionItem(
                    id = "healer_dns_optimize",
                    title = "Switch to Ultra-Fast Anycast DNS",
                    description = "${fastestDns.target.name} is responding in just ${fastestDns.rttMs} ms (faster than ISP default resolver). Configure Private DNS for instant resolution boost.",
                    impactLevel = "Medium Impact",
                    actionType = HealerActionType.OPTIMIZE_DNS,
                    isAutoFixable = false,
                    recommendedValue = if (fastestDns.target.name.contains("Cloudflare")) "one.one.one.one" else "dns.google",
                    executionNote = "Set Android Private DNS Provider to ${if (fastestDns.target.name.contains("Cloudflare")) "one.one.one.one" else "dns.google"}"
                )
            )
        }

        // 4. Broken IPv6 Fallback Delay (Happy Eyeballs penalty)
        if (dualStack?.status == DualStackStatus.IPV6_BROKEN_FALLBACK_DELAY) {
            actions.add(
                HealerActionItem(
                    id = "healer_ipv6_fix",
                    title = "Bypass Broken IPv6 Routing",
                    description = "IPv6 routes are timing out and adding a ${dualStack.happyEyeballsPenaltyMs}ms Happy Eyeballs delay to every connection setup. Switch APN to IPv4/IPv6 Dual or toggle Wi-Fi.",
                    impactLevel = "Medium Impact",
                    actionType = HealerActionType.RESET_RADIO_INTERFACE,
                    isAutoFixable = false,
                    executionNote = "Check APN protocol configuration in Cellular Network settings."
                )
            )
        }

        // 5. Degraded Wi-Fi with high bufferbloat or packet loss
        if (snapshot.primaryTransport == NetworkTransport.WIFI && (probe?.packetLossPct ?: 0f) > 0.15f) {
            actions.add(
                HealerActionItem(
                    id = "healer_wifi_fallback",
                    title = "Switch to Cellular (Wi-Fi Packet Loss)",
                    description = "Current Wi-Fi connection is dropping ${((probe?.packetLossPct ?: 0f) * 100).toInt()}% of probe packets. Fall back to Cellular to prevent application hangs.",
                    impactLevel = "High Impact",
                    actionType = HealerActionType.SWITCH_NETWORK,
                    isAutoFixable = false,
                    executionNote = "Opens Network & Internet panel to switch active transport."
                )
            )
        }

        // 6. Preventative Socket Cache & DNS Flush
        actions.add(
            HealerActionItem(
                id = "healer_flush_sockets",
                title = "Flush Local Socket Connection Pool",
                description = "Clear stale idle TCP sockets, reset TLS session tickets, and force DNS re-resolution across all app transports.",
                impactLevel = "Preventative",
                actionType = HealerActionType.FLUSH_SOCKET_CACHE,
                isAutoFixable = true,
                executionNote = "Flushes internal socket connection pools immediately."
            )
        )

        return actions
    }
}
