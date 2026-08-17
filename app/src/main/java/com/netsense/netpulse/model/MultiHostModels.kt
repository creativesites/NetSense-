package com.netsense.netpulse.model

enum class TargetCategory(val title: String, val iconName: String) {
    DNS_ANYCAST("DNS Anycast", "Dns"),
    CLOUD_CDN("Cloud & CDN Edge", "CloudQueue"),
    GAMING_VOICE("Gaming & Realtime", "SportsEsports"),
    PRODUCTIVITY("Services & APIs", "Language")
}

data class PingTarget(
    val name: String,
    val host: String,
    val ip: String,
    val category: TargetCategory,
    val port: Int = 80,
    val useTls: Boolean = false
)

data class PingTargetResult(
    val target: PingTarget,
    val rttMs: Long? = null,
    val jitterMs: Long? = null,
    val packetLossPct: Float = 0f,
    val isSuccess: Boolean = false,
    val tlsHandshakeMs: Long? = null,
    val isTlsSuccess: Boolean = false,
    val resolvedIp: String? = null,
    val statusMessage: String = "Idle"
)

enum class DualStackStatus(val title: String, val description: String, val isDegraded: Boolean) {
    FULL_DUAL_STACK("Full Dual-Stack (IPv4 & IPv6)", "Both protocols are routed optimally with zero fallback penalty.", false),
    IPV4_ONLY("IPv4-Only Network", "Standard IPv4 connectivity. IPv6 routes are absent or unprovisioned.", false),
    IPV6_ONLY_NAT64("IPv6-Only (NAT64 / DNS64)", "Pure IPv6 carrier network with NAT64 translation layer.", false),
    IPV6_BROKEN_FALLBACK_DELAY("Degraded: Broken IPv6 Route", "IPv6 route advertised but timing out (Happy Eyeballs penalty ~250-300ms).", true),
    OFFLINE("No IP Reachability", "Neither IPv4 nor IPv6 network interfaces have reachable default routes.", true)
}

data class DualStackResult(
    val ipv4Reachable: Boolean = false,
    val ipv4RttMs: Long? = null,
    val ipv4Address: String? = null,
    val ipv6Reachable: Boolean = false,
    val ipv6RttMs: Long? = null,
    val ipv6Address: String? = null,
    val happyEyeballsPenaltyMs: Long = 0L,
    val status: DualStackStatus = DualStackStatus.OFFLINE,
    val timestamp: Long = System.currentTimeMillis()
)

enum class HopNodeType(val label: String) {
    LOCAL_GATEWAY("Local Gateway / AP"),
    ISP_BACKBONE("ISP Edge / Peering"),
    DNS_RESOLVER("Anycast DNS Core"),
    CDN_EDGE("Edge Proxy / PoP"),
    TARGET_SERVER("Destination Server")
}

data class TraceHop(
    val hopIndex: Int,
    val label: String,
    val nodeType: HopNodeType,
    val ipOrHost: String,
    val rttMs: Long? = null,
    val isReachable: Boolean = true,
    val packetLossPct: Float = 0f,
    val statusNote: String = "OK"
)

data class HopTraceResult(
    val targetHost: String,
    val hops: List<TraceHop> = emptyList(),
    val totalPathLatencyMs: Long = 0L,
    val bottleneckHopIndex: Int? = null,
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
