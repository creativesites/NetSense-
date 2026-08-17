package com.example.engine

import com.example.model.DiagnosticProbeResult
import com.example.model.NetworkClassification
import com.example.model.NetworkSnapshot
import com.example.model.NetworkTransport
import com.example.model.UsabilityRating
import com.example.model.UsabilityScoreResult

/**
 * Phase 2 Usability Engine: Deterministic, Explainable Internet Scoring.
 *
 * Computes an objective 0-100 score with granular weights:
 * - 15 pts: Android Platform Validation (NET_CAPABILITY_VALIDATED)
 * - 20 pts: DNS Resolution Latency & Success
 * - 20 pts: Layer 4 TCP Handshake RTT & Jitter
 * - 20 pts: Layer 7 HTTPS / 204 Probe Status & TTFB
 * - 15 pts: Packet Reliability & Loss approximation
 * - 10 pts: Radio Link Quality (Signal Level / dBm)
 */
object UsabilityEngine {

    fun calculateScore(
        snapshot: NetworkSnapshot,
        probeResult: DiagnosticProbeResult?
    ): UsabilityScoreResult {
        if (!snapshot.isConnected || snapshot.primaryTransport == NetworkTransport.NONE) {
            return UsabilityScoreResult(
                score = 0,
                rating = UsabilityRating.UNUSABLE,
                primaryDiagnosis = "No network link active.",
                rootCauseSummary = "Modem is disconnected or device is offline.",
                explanatoryReasons = listOf(
                    "Network interface is down",
                    "No cellular or Wi-Fi radio connection detected"
                ),
                isZombieConnection = false,
                scoreBreakdown = emptyMap()
            )
        }

        var platformScore = 0 // max 15
        var dnsScore = 0 // max 20
        var tcpScore = 0 // max 20
        var httpScore = 0 // max 20
        var packetLossScore = 0 // max 15
        var signalScore = 0 // max 10

        val reasons = mutableListOf<String>()
        val breakdown = mutableMapOf<String, Int>()

        // 1. Android Platform Validation (15 pts)
        if (snapshot.isValidated) {
            platformScore = 15
            reasons.add("✓ Android OS validated active Internet connectivity")
        } else {
            platformScore = 0
            reasons.add("⚠ Android OS reports Internet UNVALIDATED (gateway unverified)")
        }
        breakdown["Platform Validation"] = platformScore

        // 2. Radio Signal Level (10 pts)
        val signalLevel = snapshot.signalLevel ?: 2
        signalScore = when (signalLevel) {
            4 -> 10
            3 -> 8
            2 -> 5
            1 -> 3
            else -> 1
        }
        breakdown["Radio Signal Quality"] = signalScore

        // 3. Probing Layer Evaluation
        if (probeResult != null && !probeResult.isRunning) {
            // DNS (20 pts)
            if (probeResult.overallDnsSuccess) {
                val dnsMs = probeResult.averageDnsMs ?: 150L
                dnsScore = when {
                    dnsMs < 50 -> 20
                    dnsMs < 120 -> 16
                    dnsMs < 300 -> 10
                    else -> 5
                }
                reasons.add("✓ DNS resolved in ${dnsMs}ms")
            } else {
                dnsScore = 0
                reasons.add("✗ DNS resolution failed across tested nameservers")
            }

            // TCP (20 pts)
            if (probeResult.overallTcpSuccess) {
                val tcpMs = probeResult.averageTcpMs ?: 140L
                tcpScore = when {
                    tcpMs < 70 -> 20
                    tcpMs < 160 -> 16
                    tcpMs < 350 -> 10
                    else -> 4
                }
                val jitterText = probeResult.tcpJitterMs?.let { " (Jitter: ±${it}ms)" } ?: ""
                reasons.add("✓ TCP handshake established in ${tcpMs}ms$jitterText")
            } else {
                tcpScore = 0
                reasons.add("✗ TCP handshake timed out — socket layer unreachable")
            }

            // HTTP / HTTPS (20 pts)
            if (probeResult.overallHttpSuccess) {
                val httpMs = probeResult.averageHttpMs ?: 220L
                httpScore = when {
                    httpMs < 160 -> 20
                    httpMs < 350 -> 15
                    httpMs < 700 -> 8
                    else -> 4
                }
                reasons.add("✓ HTTPS 204 verified in ${httpMs}ms")
            } else {
                httpScore = 0
                reasons.add("✗ HTTPS probe failed (${probeResult.failureReason ?: "no response"})")
            }

            // Packet Loss (15 pts)
            val loss = probeResult.packetLossPct
            packetLossScore = when {
                loss <= 0.01f -> 15
                loss < 0.15f -> 10
                loss < 0.35f -> 5
                else -> 0
            }
            if (loss > 0.01f) {
                reasons.add("⚠ Estimated packet loss is ${(loss * 100).toInt()}%")
            } else {
                reasons.add("✓ Zero packet loss detected across test sockets")
            }
        } else {
            // Default baseline estimation
            if (snapshot.isValidated) {
                dnsScore = 14
                tcpScore = 14
                httpScore = 14
                packetLossScore = 13
                reasons.add("• Platform reports connected link; live deep probe pending")
            } else {
                dnsScore = 4
                tcpScore = 4
                httpScore = 2
                packetLossScore = 4
                reasons.add("⚠ Link unvalidated; run live diagnostics to verify traffic")
            }
        }

        breakdown["DNS Resolution"] = dnsScore
        breakdown["TCP Handshake"] = tcpScore
        breakdown["HTTP Response"] = httpScore
        breakdown["Packet Reliability"] = packetLossScore

        val totalScore = (platformScore + dnsScore + tcpScore + httpScore + packetLossScore + signalScore)
            .coerceIn(0, 100)

        val rating = UsabilityRating.fromScore(totalScore)

        // Zombie Detection Logic: Radio signal level is >= 2 bars, connected, but HTTP or DNS completely dead
        val isZombie = (snapshot.isConnected &&
                (signalLevel >= 2) &&
                (!snapshot.isValidated || (probeResult != null && !probeResult.overallHttpSuccess && !probeResult.overallDnsSuccess)))

        val primaryDiagnosis = when {
            isZombie -> "Zombie Connection Detected: Cellular radio is strong (${signalLevel}/4 bars), but upstream Internet data flow is stalled."
            rating == UsabilityRating.OPTIMAL -> "Optimal Internet Performance: Rapid DNS, low TCP RTT, and high end-to-end responsiveness."
            rating == UsabilityRating.GOOD -> "Good Internet Usability: Web browsing, chat, and media streaming function reliably."
            rating == UsabilityRating.DEGRADED -> "Degraded Internet: Elevated latency or packet loss causing noticeable jitter and delays."
            rating == UsabilityRating.POOR -> "Poor Usability: Intermittent timeouts and sluggish request completion."
            else -> "Unusable Internet: Connection drops or packets fail to traverse gateway."
        }

        val rootCause = when {
            isZombie -> "Carrier tower connection active; gateway or upstream routing failure."
            probeResult?.overallDnsSuccess == false -> "Domain Name Server (DNS) timeout."
            probeResult?.overallTcpSuccess == false -> "Layer 4 TCP handshake timeout / firewall drop."
            (probeResult?.packetLossPct ?: 0f) > 0.2f -> "High wireless interference or cell tower congestion."
            rating == UsabilityRating.OPTIMAL -> "All network layers operating normally."
            else -> "General latency variance."
        }

        return UsabilityScoreResult(
            score = totalScore,
            rating = rating,
            primaryDiagnosis = primaryDiagnosis,
            rootCauseSummary = rootCause,
            explanatoryReasons = reasons,
            isZombieConnection = isZombie,
            scoreBreakdown = breakdown
        )
    }

    fun classify(snapshot: NetworkSnapshot, scoreResult: UsabilityScoreResult): NetworkClassification {
        if (!snapshot.isConnected) return NetworkClassification.NO_NETWORK
        if (scoreResult.isZombieConnection) return NetworkClassification.RADIO_ONLY_NO_INTERNET
        if (!snapshot.isValidated) return NetworkClassification.INTERNET_UNVALIDATED
        if (scoreResult.score >= 85) return NetworkClassification.INTERNET_OPTIMAL
        if (scoreResult.score >= 60) return NetworkClassification.INTERNET_VALIDATED
        return NetworkClassification.INTERNET_DEGRADED
    }
}
