package com.netsense.netpulse.model

/**
 * The single human-facing status vocabulary for "can I use the Internet right now, and if
 * not, why" - the question the primary screen exists to answer. Every card that currently
 * invents its own tier labels (hero score card, connection status card, signal gauge, etc.)
 * should eventually read from [ProductStatusMapper.map] instead of deriving its own ad hoc
 * `when` block, so the app has exactly one deterministic answer to "what's my status" rather
 * than several independently-computed ones that can drift out of sync with each other.
 */
enum class ProductStatus(val label: String) {
    ONLINE("Online"),
    DEGRADED("Slow Connection"),
    NO_INTERNET("No Internet"),
    WEAK_SIGNAL("Weak Signal"),
    CAPTIVE_PORTAL("Sign-In Required"),
    CONNECTING("Connecting"),
    CHECKING("Checking"),
    RECOVERING("Fixing Connection"),
    RECOVERED("Connection Restored"),
    UNAVAILABLE("Offline")
}

/**
 * Deterministic mapping from PulseCore's already-computed diagnosis (UsabilityEngine /
 * RadarEngine output) to the [ProductStatus] vocabulary above. This never re-derives
 * connectivity truth itself - it only relabels what UsabilityEngine.calculateScore /
 * classify and RadarEngine already decided, so it can't disagree with the deterministic
 * diagnostics that are the source of truth.
 */
object ProductStatusMapper {

    /** Matches RadarEngine.computeStabilityScore's own cellular RSRP "critical" cutoff. */
    private const val RF_FADING_RSRP_DBM = -110

    /** Matches RadarEngine.computeStabilityScore's own Wi-Fi RSSI "weak" cutoff. */
    private const val RF_FADING_WIFI_RSSI_DBM = -82

    fun map(
        snapshot: NetworkSnapshot,
        scoreResult: UsabilityScoreResult,
        classification: NetworkClassification,
        wifiRadar: WifiRadarSnapshot,
        cellularRf: CellularRfSnapshot,
        isProbing: Boolean,
        isRecoveryPending: Boolean,
        justRecovered: Boolean = false
    ): ProductStatus = when {
        !snapshot.isConnected -> if (isProbing) ProductStatus.CONNECTING else ProductStatus.UNAVAILABLE
        isProbing && scoreResult.score == 0 -> ProductStatus.CHECKING
        justRecovered -> ProductStatus.RECOVERED
        isRecoveryPending -> ProductStatus.RECOVERING
        scoreResult.isZombieConnection || classification == NetworkClassification.RADIO_ONLY_NO_INTERNET ->
            ProductStatus.NO_INTERNET
        snapshot.isCaptivePortal -> ProductStatus.CAPTIVE_PORTAL
        isWeakRf(snapshot, wifiRadar, cellularRf) -> ProductStatus.WEAK_SIGNAL
        classification == NetworkClassification.INTERNET_DEGRADED -> ProductStatus.DEGRADED
        else -> ProductStatus.ONLINE
    }

    private fun isWeakRf(
        snapshot: NetworkSnapshot,
        wifiRadar: WifiRadarSnapshot,
        cellularRf: CellularRfSnapshot
    ): Boolean = when (snapshot.primaryTransport) {
        NetworkTransport.WIFI -> wifiRadar.isWifiConnected && wifiRadar.isRssiMeasured &&
            wifiRadar.rssiDbm < RF_FADING_WIFI_RSSI_DBM
        NetworkTransport.CELLULAR -> cellularRf.isCellularConnected && cellularRf.rsrpDbm != null &&
            cellularRf.rsrpDbm < RF_FADING_RSRP_DBM
        else -> false
    }
}
