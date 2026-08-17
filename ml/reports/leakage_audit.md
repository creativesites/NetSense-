# PulsePredictor Feature Leakage Audit

Verdict legend: SAFE (used as a model feature, verified past/present-only), SAFE_BUT_UNUSED (present/past-only but not currently a model feature), UNSAFE_AS_FEATURE (deliberately future-looking - a label, never a feature).

| Field | Used as feature? | Verdict | Justification |
|---|---|---|---|
| dnsLatencyMs / tcpRttMs / tcpJitterMs / httpTtfbMs | yes | SAFE | Measured synchronously by DiagnosticEngine at the row's own timestamp; the probe that produced them completes before the row is persisted. No dependency on any later row. |
| packetLossPct | yes | SAFE | Computed by DiagnosticEngine from that same probe round only. |
| rsrpDbm / rsrqDb / sinrDb / cqi | yes | SAFE | Read from a CellInfoLte/Nr snapshot taken at the row's own timestamp (RadarEngine.isRfDataMeasured gates this - see the data-engineering audit). No dependency on any later row. |
| wifiRssiDbm | yes | SAFE | Read from WifiInfo at the row's own timestamp (RadarEngine.isRssiMeasured gates this). |
| latency_velocity / signal_velocity (derived) | yes | SAFE | Computed strictly from `curr` and the immediately PRECEDING row within the same window (`prev`, prev.timestamp < curr.timestamp always, enforced by assert_chronological_window() below). Never looks at a row after `curr`. |
| consecutiveProbeFailures | yes | SAFE | A running count derived from probe history UP TO AND INCLUDING the current probe; computed in NetPulseViewModel before the row is persisted. |
| transport | yes | SAFE | ConnectivityManager's active transport at the row's own timestamp - a present-tense platform fact, not a forecast. |
| usabilityScore | no | SAFE_BUT_UNUSED | UsabilityEngine.calculateScore() is a pure function of THIS row's own snapshot/probe - present-state, not future. Not currently one of the 12 model features; if added later it remains safe for the same reason. |
| isValidated / isZombie | no | SAFE_BUT_UNUSED | Present-state platform/heuristic flags for THIS row. These are the ingredients LabelSemantics uses to detect a FUTURE ROW's transition and attach the resulting label back to an EARLIER row - the leakage-sensitive direction (row i's label using row i+k's state) is handled entirely in LabelResolver/label generation, never in the feature vector itself. Not currently model features. |
| isCaptivePortal / httpStatusCode | no | SAFE_BUT_UNUSED | Present-state signals for THIS row's own probe. Not currently model features (used only in likely-cause inference on the DEFINING/labeled row, never copied into another row's feature vector). |
| labelDegradation15s / labelDropout30s / labelLikelyCause (+ *Status) | no | UNSAFE_AS_FEATURE | These are look-ahead TARGETS by construction (LabelResolver looks 15-30s into the future to compute them). They must NEVER appear in a feature vector for the row they're attached to, or for any other row in the same window. audit_feature_schema() asserts this holds. |
| recoveryActionTriggered / recoverySuccess (legacy columns) | no | SAFE_BUT_UNUSED | Not currently populated by the label-resolution pipeline (superseded by RecoveryOutcomeEntity) and not a model feature. FLAGGED FOR FUTURE REVIEW: a recovery attempt/outcome pair spans time, so if these (or RecoveryOutcomeEntity fields) are ever used as a feature, the 'outcome' side must be excluded from any row at-or-before the attempt's resolution time. |
| observationQuality / isSynthetic / sessionId / networkIdHash | no | SAFE_BUT_UNUSED | Used only as filters/grouping keys before windowing (drop INVALID/SYNTHETIC rows, group by session) - never fed to the model as a predictive signal. |

## AUTOMATED CHECK: PASSED - no label leakage detected in the 12-feature schema.