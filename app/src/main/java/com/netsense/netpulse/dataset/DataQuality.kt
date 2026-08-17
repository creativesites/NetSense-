package com.netsense.netpulse.dataset

/**
 * Data-quality classification for one persisted [com.netsense.netpulse.data.TelemetryObservationEntity].
 *
 * This is the runtime counterpart of the field-level classification documented on
 * TelemetryObservationEntity (DIRECT_MEASUREMENT / DERIVED / INFERRED / PLACEHOLDER / SYNTHETIC).
 * It answers a coarser question at the row level: "can this row be trusted as training input?"
 *
 * - VALID: the row is a real device observation and at least one active-probe layer
 *   (DNS/TCP/HTTP) was actually measured this cycle.
 * - PARTIAL: the row is a real device observation, but some expected measurement is
 *   missing (e.g. a connectivity-only tick with no active probe yet, or RF/Wi-Fi signal
 *   data that the OS did not provide this cycle). Not discarded - just explicitly flagged
 *   as incomplete so a feature extractor / dataset consumer can decide how to treat it.
 * - INVALID: the row failed basic internal consistency checks (e.g. a non-positive
 *   timestamp) and should not be used for anything beyond debugging.
 * - SYNTHETIC: produced by FaultSimulator for UI testing/demo purposes. Never a real
 *   device reading. Must never enter the production training dataset (see
 *   TelemetryObservationDao's isSynthetic = 0 queries).
 */
enum class ObservationQuality {
    VALID,
    PARTIAL,
    INVALID,
    SYNTHETIC
}

/**
 * Resolution state of a look-ahead label (e.g. labelDegradation15s / labelDropout30s).
 *
 * The critical distinction this type exists to protect: a label must never be recorded
 * as `false` just because the future hasn't happened yet, or because the app stopped
 * collecting before the look-ahead window elapsed.
 *
 * - UNRESOLVED: the look-ahead window has not fully elapsed yet (or hasn't been checked
 *   yet). The label MAY still resolve to true/false later. This is a normal, expected,
 *   transient state for recent observations.
 * - RESOLVED: the look-ahead window was fully, continuously observed (or a qualifying
 *   event was found inside it) and the boolean label value can be trusted.
 * - INSUFFICIENT_DATA: the look-ahead window can never be fully observed for this
 *   observation - the session ended (app stopped / network context changed) or there is
 *   an unbridged temporal gap inside the window - before the window elapsed. This is a
 *   terminal state distinct from UNRESOLVED: it will not be revisited by future
 *   resolution passes.
 */
enum class LabelResolutionStatus {
    UNRESOLVED,
    RESOLVED,
    INSUFFICIENT_DATA
}

/**
 * Resolution state of the likely-cause label. Kept separate from [LabelResolutionStatus]
 * because "no cause could be attributed" is a normal, common, non-transient outcome
 * (most dropouts don't have one unambiguous single-signal cause) rather than a temporary
 * waiting state.
 */
enum class LikelyCauseStatus {
    /** No single clear signal justified attributing a cause. This is expected to be the common case. */
    UNLABELED,
    RESOLVED
}
