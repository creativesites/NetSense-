package com.example.engine

import com.example.model.DualStackResult
import com.example.model.DualStackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class DualStackEngine {

    suspend fun diagnoseDualStack(): DualStackResult = withContext(Dispatchers.IO) {
        val ipv4Deferred = async { probeIpv4() }
        val ipv6Deferred = async { probeIpv6() }

        val ipv4Res = ipv4Deferred.await()
        val ipv6Res = ipv6Deferred.await()

        val ipv4Reachable = ipv4Res.first
        val ipv4Rtt = ipv4Res.second
        val ipv4Addr = ipv4Res.third

        val ipv6Reachable = ipv6Res.first
        val ipv6Rtt = ipv6Res.second
        val ipv6Addr = ipv6Res.third

        // RFC 8305 Happy Eyeballs penalty calculation
        // If IPv6 DNS returns an address but socket connection fails/timeouts, it stalls user TCP setups by ~250-300ms
        val hasIpv6DnsRecord = ipv6Addr != null
        val penalty = if (hasIpv6DnsRecord && !ipv6Reachable && ipv4Reachable) 250L else 0L

        val status = when {
            ipv4Reachable && ipv6Reachable -> DualStackStatus.FULL_DUAL_STACK
            ipv4Reachable && !ipv6Reachable && hasIpv6DnsRecord -> DualStackStatus.IPV6_BROKEN_FALLBACK_DELAY
            ipv4Reachable && !ipv6Reachable -> DualStackStatus.IPV4_ONLY
            !ipv4Reachable && ipv6Reachable -> DualStackStatus.IPV6_ONLY_NAT64
            else -> DualStackStatus.OFFLINE
        }

        DualStackResult(
            ipv4Reachable = ipv4Reachable,
            ipv4RttMs = ipv4Rtt,
            ipv4Address = ipv4Addr,
            ipv6Reachable = ipv6Reachable,
            ipv6RttMs = ipv6Rtt,
            ipv6Address = ipv6Addr,
            happyEyeballsPenaltyMs = penalty,
            status = status,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun probeIpv4(): Triple<Boolean, Long?, String?> {
        return try {
            val addr = InetAddress.getByName("8.8.8.8")
            val startTime = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress(addr, 53), 1500)
            val rtt = System.currentTimeMillis() - startTime
            socket.close()
            Triple(true, rtt, addr.hostAddress)
        } catch (e: Exception) {
            Triple(false, null, null)
        }
    }

    private fun probeIpv6(): Triple<Boolean, Long?, String?> {
        return try {
            // Google Public DNS IPv6: 2001:4860:4860::8888 or Cloudflare 2606:4700:4700::1111
            val addr = InetAddress.getByName("2606:4700:4700::1111")
            val isInet6 = addr is Inet6Address
            val startTime = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress(addr, 53), 1500)
            val rtt = System.currentTimeMillis() - startTime
            socket.close()
            Triple(true, rtt, addr.hostAddress)
        } catch (e: Exception) {
            // Check if device even has IPv6 DNS resolution
            val resolvedIpv6 = try {
                val addrs = InetAddress.getAllByName("ipv6.google.com")
                addrs.firstOrNull { it is Inet6Address }?.hostAddress
            } catch (ex: Exception) {
                null
            }
            Triple(false, null, resolvedIpv6)
        }
    }
}
