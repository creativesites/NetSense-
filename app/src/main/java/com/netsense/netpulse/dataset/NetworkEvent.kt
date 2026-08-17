package com.netsense.netpulse.dataset

/**
 * A small, deliberately-bounded vocabulary of network events used for label generation and
 * analysis. Kept to exactly the set that's useful for training/analysis (Part 8) - not an
 * exhaustive taxonomy of everything that could happen to a network connection.
 */
enum class NetworkEventType {
    CONNECTIVITY_LOST,
    CONNECTIVITY_RESTORED,
    DEGRADATION_STARTED,
    DEGRADATION_ENDED,
    ZOMBIE_DETECTED,
    CAPTIVE_PORTAL_DETECTED,
    TRANSPORT_CHANGED,
    RECOVERY_STARTED,
    RECOVERY_SUCCEEDED,
    RECOVERY_FAILED
}

/**
 * A single, deterministic, explainable event derived from two consecutive same-session
 * telemetry observations (or a recovery-attempt lifecycle). Events are edge-triggered: they
 * mark the row where a transition happened, not every row that happens to already be in
 * the "bad" state.
 */
data class NetworkEvent(
    val type: NetworkEventType,
    val timestamp: Long,
    val sessionId: String,
    val observationId: Long? = null,
    val detail: String? = null
)
