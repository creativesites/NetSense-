package com.netsense.netpulse.dataset

import com.netsense.netpulse.model.NetworkSnapshot
import com.netsense.netpulse.model.NetworkTransport
import com.netsense.netpulse.telephony.TelephonySnapshot
import java.util.UUID

/**
 * Tracks a stable `sessionId` representing a contiguous period of observations that belong
 * to the same network context, so temporally unrelated observations are never treated as
 * one continuous sequence by [LabelResolver] or a future PulsePredictor sliding window.
 *
 * Example this exists to prevent: LTE, LTE, LTE, Wi-Fi, Wi-Fi must NOT be treated as one
 * uninterrupted 5-step sequence just because five rows were written back to back.
 *
 * A session rolls over (a new sessionId is minted) when this observation shows:
 *  - a primary transport change (Wi-Fi <-> Cellular <-> other), or
 *  - a Wi-Fi identity change (different BSSID) while remaining on Wi-Fi, or
 *  - a cellular carrier or radio-access-technology change (e.g. LTE -> 5G NR, or a SIM/
 *    carrier change, to the extent the OS reports it), or
 *  - connectivity being lost and later restored, or
 *  - a gap since the previous observation larger than [SESSION_GAP_THRESHOLD_MS] (the
 *    device likely slept, the app was backgrounded/killed, or collection otherwise paused
 *    long enough that we can no longer assume temporal continuity).
 *
 * This is deliberately coarse. It is not trying to model every possible network event -
 * only to stop unrelated observations from being stitched into one training sequence.
 */
class NetworkSessionManager {

    companion object {
        /** Gap above which we no longer trust temporal continuity between two observations. */
        const val SESSION_GAP_THRESHOLD_MS = 120_000L // 2 minutes
    }

    private var sessionId: String = newSessionId()
    private var lastTransport: NetworkTransport = NetworkTransport.NONE
    private var lastNetworkIdentity: String? = null
    private var lastWasConnected: Boolean = false
    private var lastObservationTimestamp: Long = 0L
    // Deliberately NOT inferred from lastObservationTimestamp == 0L - a caller-supplied
    // timestamp of exactly 0 (e.g. in tests, or epoch-relative clocks) is a legitimate real
    // value, not evidence that resolveSessionId has never been called.
    private var hasObservation: Boolean = false

    val currentSessionId: String get() = sessionId

    /**
     * Resolves the sessionId for one new telemetry observation, rolling over first if this
     * observation's context is not a continuation of the previous one. Call this once per
     * observation, in timestamp order, before persisting it.
     */
    fun resolveSessionId(
        snapshot: NetworkSnapshot,
        telephony: TelephonySnapshot,
        wifiBssid: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val identity = networkIdentity(snapshot, telephony, wifiBssid)

        val isFirstObservation = !hasObservation
        val transportChanged = !isFirstObservation &&
            lastTransport != NetworkTransport.NONE &&
            snapshot.primaryTransport != NetworkTransport.NONE &&
            snapshot.primaryTransport != lastTransport
        val identityChanged = !isFirstObservation &&
            lastNetworkIdentity != null &&
            identity != null &&
            identity != lastNetworkIdentity &&
            snapshot.primaryTransport == lastTransport
        val reconnected = !isFirstObservation && !lastWasConnected && snapshot.isConnected
        val largeGap = !isFirstObservation && (timestamp - lastObservationTimestamp) > SESSION_GAP_THRESHOLD_MS

        if (transportChanged || identityChanged || reconnected || largeGap) {
            sessionId = newSessionId()
        }

        lastTransport = snapshot.primaryTransport
        lastNetworkIdentity = identity
        lastWasConnected = snapshot.isConnected
        lastObservationTimestamp = timestamp
        hasObservation = true

        return sessionId
    }

    /** Forces a new session, e.g. on app start or an explicit user-triggered reset. */
    fun reset() {
        sessionId = newSessionId()
        lastTransport = NetworkTransport.NONE
        lastNetworkIdentity = null
        lastWasConnected = false
        lastObservationTimestamp = 0L
        hasObservation = false
    }

    /**
     * A coarse, privacy-preserving "which network is this" signature used only to detect
     * transitions - never persisted directly (BSSID/carrier name are not written to the
     * telemetry table; only the derived sessionId is).
     */
    private fun networkIdentity(
        snapshot: NetworkSnapshot,
        telephony: TelephonySnapshot,
        wifiBssid: String?
    ): String? = when (snapshot.primaryTransport) {
        NetworkTransport.WIFI -> wifiBssid ?: snapshot.interfaceName
        NetworkTransport.CELLULAR -> listOfNotNull(telephony.carrierName, telephony.networkType)
            .joinToString("|").ifBlank { null }
        else -> snapshot.interfaceName
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()
}
