package com.netsense.netpulse.dataset

import com.netsense.netpulse.data.TelemetryObservationEntity

/**
 * Deterministic, versioned definitions of what counts as a "degradation" or "dropout"
 * event for PulsePredictor label generation.
 *
 * Thresholds are pulled directly from the score bands and RF thresholds PulseCore already
 * uses elsewhere (UsabilityRating.GOOD.minScore = 60, and the same jitter/RSRP/RSSI cutoffs
 * RadarEngine.computeStabilityScore already applies) rather than invented ad hoc, per the
 * instruction that label semantics be derived from existing PulseCore/UsabilityEngine
 * semantics.
 *
 * Bump [LABEL_SCHEMA_VERSION] whenever these definitions change. A stored
 * `labelSchemaVersion` on a resolved row tells a future consumer exactly which definitions
 * produced it; never reinterpret an old row's boolean value under a new schema version -
 * re-resolve it from raw data instead.
 */
object LabelSemantics {

    const val LABEL_SCHEMA_VERSION = 1

    /** Look-ahead horizon for the "is this about to degrade" label. */
    const val DEGRADATION_LOOKAHEAD_MS = 15_000L

    /** Look-ahead horizon for the "is this about to drop out entirely" label. */
    const val DROPOUT_LOOKAHEAD_MS = 30_000L

    /** A usability score below this falls out of UsabilityRating.GOOD-or-better. */
    private const val DEGRADATION_SCORE_THRESHOLD = 60

    /** Matches RadarEngine.computeStabilityScore's own "critical" cellular RSRP cutoff. */
    private const val RF_FADING_RSRP_DBM = -110

    /** Matches RadarEngine.computeStabilityScore's own "weak" Wi-Fi RSSI cutoff. */
    private const val RF_FADING_WIFI_RSSI_DBM = -82

    /** Matches RadarEngine.computeStabilityScore's own "severe jitter" cutoff. */
    private const val CONGESTION_JITTER_MS = 150L

    /** Every active-probe layer failing at once is the clearest possible dropout signature. */
    private fun isTotalProbeFailure(obs: TelemetryObservationEntity): Boolean =
        !obs.dnsSuccess && !obs.tcpSuccess && !obs.httpSuccess

    /**
     * True on the real-data captive-portal signature (HTTP 301/302 redirect, matching
     * TroubleshootEngine's own heuristic), OR on isCaptivePortal (currently only ever set
     * by FaultSimulator - see TelemetryObservationEntity's field documentation).
     */
    private fun isCaptivePortalSignature(obs: TelemetryObservationEntity): Boolean =
        obs.isCaptivePortal || obs.httpStatusCode == 301 || obs.httpStatusCode == 302

    /** True if [obs] itself represents a degraded-or-worse usability state (a level, not an edge). */
    fun isDegradedState(obs: TelemetryObservationEntity): Boolean =
        obs.usabilityScore < DEGRADATION_SCORE_THRESHOLD

    /**
     * True if [obs] itself represents a "validated Internet is unavailable" state: a zombie
     * connection, an unvalidated link, or every probed layer failing simultaneously.
     */
    fun isDropoutState(obs: TelemetryObservationEntity): Boolean =
        obs.isZombie || !obs.isValidated || isTotalProbeFailure(obs)

    /**
     * Detects edge-triggered events between two consecutive same-session observations.
     * Only transitions are reported: an observation that is already broken does not
     * generate a new event just because it's still broken (Part 6/8 - the model should
     * learn to predict the event BEFORE it happens, not re-flag an ongoing outage).
     *
     * [previous] must be the immediately preceding observation in the SAME session, or
     * null if [current] is the first observation of its session.
     */
    fun detectEvents(previous: TelemetryObservationEntity?, current: TelemetryObservationEntity): List<NetworkEvent> {
        val events = mutableListOf<NetworkEvent>()

        val wasDegraded = previous?.let { isDegradedState(it) } ?: false
        val isDegraded = isDegradedState(current)
        when {
            isDegraded && !wasDegraded -> events += NetworkEvent(
                NetworkEventType.DEGRADATION_STARTED, current.timestamp, current.sessionId, current.id
            )
            !isDegraded && wasDegraded -> events += NetworkEvent(
                NetworkEventType.DEGRADATION_ENDED, current.timestamp, current.sessionId, current.id
            )
        }

        val wasDown = previous?.let { isDropoutState(it) } ?: false
        val isDown = isDropoutState(current)
        when {
            isDown && !wasDown -> {
                events += NetworkEvent(NetworkEventType.CONNECTIVITY_LOST, current.timestamp, current.sessionId, current.id)
                if (current.isZombie) {
                    events += NetworkEvent(NetworkEventType.ZOMBIE_DETECTED, current.timestamp, current.sessionId, current.id)
                }
                if (isCaptivePortalSignature(current)) {
                    events += NetworkEvent(NetworkEventType.CAPTIVE_PORTAL_DETECTED, current.timestamp, current.sessionId, current.id)
                }
            }
            !isDown && wasDown -> events += NetworkEvent(
                NetworkEventType.CONNECTIVITY_RESTORED, current.timestamp, current.sessionId, current.id
            )
        }

        if (previous != null && previous.transport != current.transport) {
            events += NetworkEvent(NetworkEventType.TRANSPORT_CHANGED, current.timestamp, current.sessionId, current.id)
        }

        return events
    }

    /**
     * Conservative, evidence-gated cause inference. Only assigns a cause when [obs] is
     * itself in a dropout state AND exactly one clear, unambiguous signal explains it.
     * Returns null (UNLABELED) far more often than not by design - a wrong cause label is
     * worse for training than no cause label (Part 17).
     */
    fun inferLikelyCause(obs: TelemetryObservationEntity): String? {
        if (!isDropoutState(obs)) return null
        return when {
            isCaptivePortalSignature(obs) -> "CAPTIVE_PORTAL"
            !obs.dnsSuccess && obs.tcpSuccess -> "DNS_BLACKHOLE"
            obs.isZombie && !obs.tcpSuccess && !obs.dnsSuccess -> "GATEWAY_DEAD"
            obs.rsrpDbm != null && obs.rsrpDbm < RF_FADING_RSRP_DBM -> "RF_FADING"
            obs.wifiRssiDbm != null && obs.wifiRssiDbm < RF_FADING_WIFI_RSSI_DBM -> "RF_FADING"
            obs.tcpSuccess && obs.tcpJitterMs != null && obs.tcpJitterMs > CONGESTION_JITTER_MS -> "UPSTREAM_CONGESTION"
            else -> null
        }
    }
}
