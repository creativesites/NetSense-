package com.example.engine

import com.example.model.DiagnosticMode
import com.example.model.DiagnosticProbeResult
import com.example.model.EndpointProbeDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/**
 * Phase 2 Diagnostic Engine: High-precision, lightweight active network probes.
 *
 * Capabilities:
 * 1. Multi-endpoint probing (Google gstatic + Cloudflare)
 * 2. DNS query resolution timing & IP retrieval
 * 3. Layer 4 TCP Handshake RTT & Jitter variance
 * 4. Layer 7 HTTP 204 response timing
 * 5. Multi-probe packet loss estimation
 */
class DiagnosticEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .writeTimeout(3500, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {

    suspend fun runProbe(mode: DiagnosticMode = DiagnosticMode.STANDARD): DiagnosticProbeResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val primaryHost = "connectivitycheck.gstatic.com"
            val secondaryHost = "cloudflare.com"

            val primaryDetail = probeSingleEndpoint(primaryHost, isHttpNeeded = mode != DiagnosticMode.MICRO)
            
            var secondaryDetail: EndpointProbeDetail? = null
            if (mode == DiagnosticMode.DEEP || !primaryDetail.httpSuccess) {
                secondaryDetail = probeSingleEndpoint(secondaryHost, isHttpNeeded = true)
            }

            // Calculate TCP Jitter across 2 successive socket handshakes if deep mode
            var tcpJitterMs: Long? = null
            if (mode == DiagnosticMode.DEEP && primaryDetail.tcpHandshakeMs != null) {
                val secondRtt = measureSingleTcpRtt("8.8.8.8", 53)
                if (secondRtt != null) {
                    tcpJitterMs = abs(primaryDetail.tcpHandshakeMs - secondRtt)
                }
            }

            // Packet Loss Approximation (3 to 5 micro socket checks)
            val probeCount = if (mode == DiagnosticMode.DEEP) 5 else 3
            var receivedCount = 0
            val targetServers = listOf("8.8.8.8", "1.1.1.1", "9.9.9.9")

            for (i in 0 until probeCount) {
                val host = targetServers[i % targetServers.size]
                val success = testSocketReachability(host, 53, 1200)
                if (success) receivedCount++
            }

            val packetLossPct = ((probeCount - receivedCount).toFloat() / probeCount.toFloat())
                .coerceIn(0f, 1f)

            var failureReason: String? = null
            if (!primaryDetail.dnsSuccess && secondaryDetail?.dnsSuccess != true) {
                failureReason = "DNS Resolution Failed: Unable to resolve hostnames via carrier DNS."
            } else if (!primaryDetail.tcpSuccess && secondaryDetail?.tcpSuccess != true) {
                failureReason = "TCP Handshake Timed Out: Socket connections blocked or cellular gateway stalling."
            } else if (!primaryDetail.httpSuccess && secondaryDetail?.httpSuccess != true && mode != DiagnosticMode.MICRO) {
                failureReason = "HTTP 204 Probe Failed: Upstream Internet data transmission failed."
            } else if (packetLossPct >= 0.5f) {
                failureReason = "Severe Packet Loss: ${(packetLossPct * 100).toInt()}% packet loss detected."
            }

            DiagnosticProbeResult(
                timestamp = System.currentTimeMillis(),
                isRunning = false,
                mode = mode,
                primaryEndpoint = primaryDetail,
                secondaryEndpoint = secondaryDetail,
                tcpJitterMs = tcpJitterMs,
                packetLossPct = packetLossPct,
                totalProbesSent = probeCount,
                totalProbesReceived = receivedCount,
                failureReason = failureReason,
                durationMs = System.currentTimeMillis() - startTime
            )
        }

    private fun probeSingleEndpoint(host: String, isHttpNeeded: Boolean): EndpointProbeDetail {
        var dnsSuccess = false
        var dnsLookupMs: Long? = null
        var resolvedIp: InetAddress? = null

        var tcpSuccess = false
        var tcpHandshakeMs: Long? = null

        var httpSuccess = false
        var httpLatencyMs: Long? = null
        var httpStatusCode: Int? = null

        // 1. DNS Resolution
        try {
            dnsLookupMs = measureTimeMillis {
                val addresses = InetAddress.getAllByName(host)
                if (addresses.isNotEmpty()) {
                    resolvedIp = addresses.first()
                    dnsSuccess = true
                }
            }
        } catch (_: Exception) {
            dnsSuccess = false
        }

        // 2. TCP Layer 4 Handshake
        if (dnsSuccess && resolvedIp != null) {
            try {
                tcpHandshakeMs = measureTimeMillis {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(resolvedIp, 443), 2800)
                        tcpSuccess = true
                    }
                }
            } catch (_: Exception) {
                tcpSuccess = false
            }
        }

        // 3. HTTP Layer 7 Probe
        if (isHttpNeeded) {
            try {
                val request = Request.Builder()
                    .url("https://$host/generate_204")
                    .header("User-Agent", "NetPulse-Diagnostics/2.0")
                    .header("Cache-Control", "no-cache")
                    .build()

                httpLatencyMs = measureTimeMillis {
                    client.newCall(request).execute().use { response ->
                        httpStatusCode = response.code
                        httpSuccess = response.isSuccessful || response.code == 204
                    }
                }
            } catch (_: Exception) {
                httpSuccess = false
            }
        }

        return EndpointProbeDetail(
            endpointHost = host,
            ipAddress = resolvedIp?.hostAddress,
            dnsLookupMs = dnsLookupMs,
            dnsSuccess = dnsSuccess,
            tcpHandshakeMs = tcpHandshakeMs,
            tcpSuccess = tcpSuccess,
            httpStatusCode = httpStatusCode,
            httpLatencyMs = httpLatencyMs,
            httpSuccess = httpSuccess
        )
    }

    private fun measureSingleTcpRtt(host: String, port: Int): Long? {
        return try {
            measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1800)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun testSocketReachability(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
