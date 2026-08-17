package com.netsense.netpulse.engine

import com.netsense.netpulse.model.PingTarget
import com.netsense.netpulse.model.PingTargetResult
import com.netsense.netpulse.model.TargetCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.abs

class PingMatrixEngine {

    companion object {
        val DEFAULT_TARGETS = listOf(
            // DNS Anycast
            PingTarget("Cloudflare DNS", "1.1.1.1", "1.1.1.1", TargetCategory.DNS_ANYCAST, port = 53),
            PingTarget("Google DNS", "8.8.8.8", "8.8.8.8", TargetCategory.DNS_ANYCAST, port = 53),
            PingTarget("Quad9 Security", "9.9.9.9", "9.9.9.9", TargetCategory.DNS_ANYCAST, port = 53),
            PingTarget("OpenDNS Home", "208.67.222.222", "208.67.222.222", TargetCategory.DNS_ANYCAST, port = 53),

            // Cloud & CDN Edges
            PingTarget("Cloudflare Edge", "cloudflare.com", "104.16.132.229", TargetCategory.CLOUD_CDN, port = 443, useTls = true),
            PingTarget("AWS CloudFront", "aws.amazon.com", "13.32.0.1", TargetCategory.CLOUD_CDN, port = 443, useTls = true),
            PingTarget("Google Edge CDN", "google.com", "142.250.190.46", TargetCategory.CLOUD_CDN, port = 443, useTls = true),
            PingTarget("Fastly Edge", "fastly.com", "151.101.1.69", TargetCategory.CLOUD_CDN, port = 443, useTls = true),

            // Gaming & Voice Low-Latency
            PingTarget("Discord Gateway", "discord.com", "162.159.135.232", TargetCategory.GAMING_VOICE, port = 443, useTls = true),
            PingTarget("Steam Storefront", "store.steampowered.com", "104.102.193.187", TargetCategory.GAMING_VOICE, port = 443, useTls = true),
            PingTarget("Riot Games US", "riotgames.com", "104.18.23.111", TargetCategory.GAMING_VOICE, port = 443, useTls = true),

            // Productivity & Core Web
            PingTarget("GitHub Global", "github.com", "140.82.121.3", TargetCategory.PRODUCTIVITY, port = 443, useTls = true),
            PingTarget("Microsoft Azure", "portal.azure.com", "20.60.0.1", TargetCategory.PRODUCTIVITY, port = 443, useTls = true),
            PingTarget("Slack Realtime", "slack.com", "157.240.22.35", TargetCategory.PRODUCTIVITY, port = 443, useTls = true)
        )
    }

    suspend fun executeMatrixProbe(): List<PingTargetResult> = probeMatrix()

    suspend fun probeMatrix(
        targets: List<PingTarget> = DEFAULT_TARGETS,
        timeoutMs: Int = 1200
    ): List<PingTargetResult> = withContext(Dispatchers.IO) {
        val deferred = targets.map { target ->
            async { probeSingleTarget(target, timeoutMs) }
        }
        deferred.awaitAll()
    }

    private fun probeSingleTarget(target: PingTarget, timeoutMs: Int): PingTargetResult {
        var resolvedIp: String? = null
        val rttSamples = mutableListOf<Long>()
        var tlsHandshakeTime: Long? = null
        var lastError: String? = null
        var successfulProbes = 0
        val probesCount = 2

        // Step 1: DNS Resolution
        try {
            val addresses = InetAddress.getAllByName(target.host)
            if (addresses.isNotEmpty()) {
                resolvedIp = addresses[0].hostAddress
            }
        } catch (e: Exception) {
            resolvedIp = target.ip
        }

        val hostToConnect = resolvedIp ?: target.ip

        for (i in 0 until probesCount) {
            try {
                val socket = Socket()
                val startTime = System.currentTimeMillis()
                socket.connect(InetSocketAddress(hostToConnect, target.port), timeoutMs)
                val rtt = System.currentTimeMillis() - startTime
                rttSamples.add(rtt)
                successfulProbes++

                // Measure TLS Handshake if requested
                if (target.useTls && tlsHandshakeTime == null && i == 0) {
                    try {
                        val sslStart = System.currentTimeMillis()
                        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                        val sslSocket = sslFactory.createSocket(socket, target.host, target.port, true) as SSLSocket
                        sslSocket.soTimeout = timeoutMs
                        sslSocket.startHandshake()
                        tlsHandshakeTime = System.currentTimeMillis() - sslStart
                        sslSocket.close()
                    } catch (e: Exception) {
                        tlsHandshakeTime = null
                    }
                } else {
                    socket.close()
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Timeout"
            }
        }

        val isSuccess = successfulProbes > 0
        val avgRtt = if (rttSamples.isNotEmpty()) rttSamples.average().toLong() else null
        val jitter = if (rttSamples.size > 1) abs(rttSamples[0] - rttSamples[1]) else 0L
        val lossPct = if (probesCount > 0) (probesCount - successfulProbes).toFloat() / probesCount else 1f

        val statusMsg = when {
            isSuccess && avgRtt != null && avgRtt < 50 -> "Optimal (<50ms)"
            isSuccess && avgRtt != null && avgRtt < 150 -> "Normal (${avgRtt}ms)"
            isSuccess && avgRtt != null -> "Slow (${avgRtt}ms)"
            else -> lastError ?: "Failed"
        }

        return PingTargetResult(
            target = target,
            rttMs = avgRtt,
            jitterMs = jitter,
            packetLossPct = lossPct,
            isSuccess = isSuccess,
            tlsHandshakeMs = tlsHandshakeTime,
            isTlsSuccess = tlsHandshakeTime != null,
            resolvedIp = resolvedIp,
            statusMessage = statusMsg
        )
    }
}
