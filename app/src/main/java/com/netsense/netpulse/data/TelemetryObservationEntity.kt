package com.netsense.netpulse.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of raw telemetry for the future PulsePredictor training dataset.
 *
 * FIELD CLASSIFICATION (Part 2 of the NetPulse data-engineering audit). Every field below
 * is tagged with where its value actually comes from, so nothing here is ever mistaken for
 * "real" simply because it exists as a column:
 *
 *   A. DIRECT_MEASUREMENT - read straight from an Android/network API this cycle.
 *   B. DERIVED             - computed deterministically from one or more measurements.
 *   C. INFERRED            - produced by diagnostic/heuristic logic combining several signals.
 *   D. PLACEHOLDER         - the column exists but is not reliably populated yet.
 *   E. SYNTHETIC           - only ever written when isSynthetic = true (FaultSimulator).
 *
 *   id                        - Room-assigned row identity. (n/a)
 *   timestamp                 - A. System.currentTimeMillis() at observation time.
 *   sessionId                 - B. NetworkSessionManager output; groups observations that
 *                                  belong to the same contiguous network context.
 *   transport                 - A. ConnectivityManager active transport.
 *   networkId                 - A. Interface name or carrier name (never raw SSID/BSSID).
 *   rsrpDbm / rsrqDb / sinrDb  - A when isRfDataMeasured was true on the source
 *   / cqi                        CellularRfSnapshot at collection time, otherwise these are
 *                                  null - NEVER a plausible-looking placeholder number. See
 *                                  RadarEngine.getCellularRfSnapshot.
 *   wifiRssiDbm                - A when the source WifiRadarSnapshot.isRssiMeasured was
 *                                  true, otherwise null.
 *   dnsLatencyMs / tcpRttMs /  - A when the corresponding probe layer actually ran and
 *   tcpJitterMs / httpTtfbMs      succeeded, null when the probe didn't run or that layer
 *                                  failed to produce a timing. NEVER defaulted to 0.
 *   dnsSuccess / tcpSuccess /  - A. Direct pass/fail from DiagnosticEngine for this cycle.
 *   httpSuccess
 *   isCaptivePortal            - A/D. Direct pass-through of NetworkSnapshot.isCaptivePortal,
 *                                  BUT as of this phase nothing in the production
 *                                  connectivity pipeline actually sets that upstream field
 *                                  to true yet - it is currently only ever true for
 *                                  FaultSimulator rows (isSynthetic = true). Treat it as a
 *                                  structural placeholder for real observations until a
 *                                  real captive-portal detector is wired into
 *                                  ConnectivityMonitor/NetPulseViewModel; httpStatusCode
 *                                  below (302/301) is the more reliable real-data signal
 *                                  for now (see TroubleshootEngine's own use of it).
 *   httpStatusCode             - A. Primary probe endpoint's HTTP status code, when an HTTP
 *                                  probe ran (e.g. 302 is the captive-portal signature).
 *   packetLossPct              - B. Derived by DiagnosticEngine from multi-socket probing.
 *   consecutiveProbeFailures   - B. Derived in NetPulseViewModel from probe success history.
 *   usabilityScore             - C. UsabilityEngine's deterministic composite score.
 *   isValidated                - A. NET_CAPABILITY_VALIDATED from ConnectivityManager.
 *   isZombie                   - C. UsabilityEngine's zombie-connection heuristic.
 *   primaryDiagnosis           - C. UsabilityEngine's human-readable diagnosis string.
 *   observationQuality         - B. ObservationQuality, computed at write time (see
 *                                  ObservationQualityClassifier).
 *   isSynthetic                - B. true only for FaultSimulator-sourced rows. Production
 *                                  dataset queries must filter isSynthetic = 0.
 *   recoveryActionTriggered /  - D. Reserved; superseded by RecoveryOutcomeEntity, which
 *   recoverySuccess               tracks recovery attempts with real pre/post validation.
 *                                  Kept here only for backward read-compatibility.
 *   labelDegradation15s /      - B. Computed by LabelResolver from FUTURE observations in
 *   labelDropout30s               the same session. null while unresolved/insufficient.
 *   label*Status               - B. LabelResolutionStatus - see DataQuality.kt. This is
 *                                  what lets a consumer tell "not yet known" apart from
 *                                  "measured false".
 *   labelAnomaly                - D. Reserved for a future anomaly-detection label; not
 *                                  populated by this phase's LabelResolver.
 *   labelLikelyCause /          - C. Conservative, evidence-gated cause inference; UNLABELED
 *   labelLikelyCauseStatus         (not a random guess) when no single clear signal exists.
 *   labelSchemaVersion          - B. LabelSemantics.LABEL_SCHEMA_VERSION at resolution time,
 *                                  so a future consumer never has to guess which label
 *                                  definitions produced this row.
 */
@Entity(tableName = "ml_telemetry_observations")
data class TelemetryObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String,
    val transport: String,
    val networkId: String? = null,
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val sinrDb: Int? = null,
    val cqi: Int? = null,
    val wifiRssiDbm: Int? = null,
    val dnsLatencyMs: Long? = null,
    val dnsSuccess: Boolean,
    val tcpRttMs: Long? = null,
    val tcpSuccess: Boolean,
    val tcpJitterMs: Long? = null,
    val httpTtfbMs: Long? = null,
    val httpSuccess: Boolean,
    val isCaptivePortal: Boolean = false,
    val httpStatusCode: Int? = null,
    val packetLossPct: Float,
    val consecutiveProbeFailures: Int,
    val usabilityScore: Int,
    val isValidated: Boolean,
    val isZombie: Boolean,
    val primaryDiagnosis: String,
    val observationQuality: String,
    val isSynthetic: Boolean = false,
    val recoveryActionTriggered: String? = null,
    val recoverySuccess: Boolean? = null,
    // Future-outcome labels, resolved after the fact by LabelResolver - see DataQuality.kt
    // for why every label is paired with an explicit resolution-status column.
    val labelDegradation15s: Boolean? = null,
    val labelDegradation15sStatus: String = "UNRESOLVED",
    val labelDropout30s: Boolean? = null,
    val labelDropout30sStatus: String = "UNRESOLVED",
    val labelAnomaly: Boolean? = null,
    val labelLikelyCause: String? = null,
    val labelLikelyCauseStatus: String = "UNLABELED",
    val labelSchemaVersion: Int = 0
)
