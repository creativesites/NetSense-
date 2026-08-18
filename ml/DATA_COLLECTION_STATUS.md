# NetPulse PulsePredictor - Data Collection Status

**Status: NOT READY (progress: first real export received and validated 2026-08-18).**

This is not a placeholder result - it is the honest, current state of data collection.
This document exists so that fact is never lost or quietly assumed away later.

## Progress log

### 2026-08-18 - first real export (`data/raw_exports/20260818_0921_s20-airtel-zm_001.jsonl`)

1,729 real observations from a Samsung S20 on Airtel Zambia, ~35 minutes of collection,
21 distinct sessions. Ran `validate_dataset.py` (PASSED - schema, leakage, and structural
checks all clean) and `inspect_dataset.py` for the first time against real data. Actual
numbers vs. the collection target below:

| Target | Minimum | Actual (this export) | Met? |
|---|---|---|---|
| Distinct sessions | >= 30 | 21 | Not yet |
| RESOLVED `dropout30s` positives, >= 10 sessions | >= 30 across >= 10 sessions | 71 positives, but only **6 sessions** | Count yes, diversity no |
| RESOLVED `degradation15s` positives | >= 50 | 23 | Not yet |
| Wi-Fi represented (>= 5 sessions) | required | **0 sessions** (100% CELLULAR, some NONE) | Not met |
| Distinct networks (`networkIdHash`) | >= 3 | 3 (1680 / 34 / 15 obs - heavily dominated by one) | Technically yes, badly skewed |
| Time span | >= 2 weeks | ~35 minutes, one sitting | Not met |

Two things this export makes clear beyond the raw counts:

- **Almost no negative examples exist.** `dropout30s` resolved 71 positive vs. **1**
  negative; `degradation15s` resolved 23 positive vs. 6 negative. This collection window
  captured a real incident (matches the zombie episode seen in the earlier
  `diagnostic_logs` export), which is exactly the kind of signal we want, but a model
  needs comparable coverage of "stayed healthy" stretches too, or it will learn to
  always predict failure. **Collecting some boring, uneventful sessions is now just as
  valuable as capturing more incidents.**
- **71 dropout positives came from only 6 sessions, and 29 of those from one single
  session.** `MIN_SESSIONS_FOR_HOLDOUT_SPLIT = 10` in `src/splitting.py` means a held-out
  split isn't even attemptable yet on `dropout30s` alone from this file (need
  `train_baselines.py`/`train_cnn.py` to see >= 10 sessions with a resolved label, and we
  have 6). More sessions, not more rows within the same few sessions, is the actual
  bottleneck.

**Two genuine findings, not yet acted on (holding for your call, per "be conservative"):**

1. **`rsrpDbm` / `sinrDb` / `cqi` are null in all 1,729 rows** - 3 of the 12 model
   features are 100% missing in this export. `RadarEngine.getCellularRfSnapshot()` only
   populates these from a registered `CellInfoLte`/`CellInfoNr` reading
   (`isRfDataMeasured`), which typically requires `ACCESS_FINE_LOCATION` to be granted at
   runtime (a coarse/foreground grant may not be enough for detailed cell info on some
   OEM builds). **Worth checking Android Settings -> Apps -> NetPulse -> Permissions ->
   Location on the S20** - if it's "Denied" or "Only this app - never", that would fully
   explain this. This is the single highest-leverage thing to fix before collecting more
   data, since it's silently costing 3 of 12 features on every single observation.
2. **DNS/TCP/HTTP/packet-loss features are "sticky", not independently sampled per
   observation.** They come from `NetPulseViewModel._uiState.value.probeResult` - the
   result of the last explicit `runActiveProbe()` call (init, a manual "Run Diagnostics"
   tap, or the socket-flush healer action) - reused across every ~1-second telemetry tick
   until the next probe runs. That's why `tcpRttMs` showed up as a "constant" flag in the
   inspector: 22 consecutive rows all reading exactly 268.0ms is one real probe result
   getting stamped across ~35 seconds of ticks, not a bug in the probe itself. This isn't
   necessarily wrong, but it does mean a 15-step window's "15 timesteps" often contain
   far fewer than 15 independent probe-layer measurements - worth keeping in mind for
   later feature/architecture decisions, not something to fix now.

**Good news, confirmed for the first time with real numbers:** the cadence concern raised
after the (wrong-table) `diagnostic_logs` export does NOT apply to the real ML table.
Median inter-observation interval here is **1.09 seconds** (not ~58s), so a
`WINDOW_SIZE=15` sequence spans **~15.3 seconds** of wall clock time - very close to the
original design assumption. `ml_telemetry_observations` is fed by the ViewModel's live
connectivity/telephony flow, not the slow Sentinel loop, and that's exactly what we see.

## What was checked

- `find . -iname "*.csv" -o -iname "*.jsonl" -o -iname "*dataset*"` across the repository:
  only source code (`DatasetExportService.kt`, its test, this `ml/` directory) - no export
  files.
- No `netpulse_database.db` (or any Room database file) exists anywhere on this machine.
- This is a server-side development/CI environment. `DatasetExportService`,
  `TelemetryObservationDao`, `NetworkSessionManager`, and `LabelResolutionService` all
  require live Android platform APIs (`TelephonyManager`, `WifiManager`,
  `ConnectivityManager`, real network sockets) that do not exist here. **The Android app
  has never run against a real device or real network in this environment, so it has
  never produced telemetry, let alone an export.**

## What DOES exist (and is validated)

The full collection → labeling → export pipeline, built and unit-tested in the prior
phase (commit `4a62d5a`, 51/51 Kotlin tests passing), plus a complete offline ML lab
(`ml/`, this directory, 55/55 Python tests passing) ready to consume a real export the
moment one exists:

- `NetworkSessionManager`, `ObservationQualityClassifier`, `LabelResolver`,
  `LabelResolutionService`, `RecoveryOutcomeTracker`, `DatasetExportService`,
  `TrainingSequenceBuilder` (Kotlin, on-device).
- `ml/src/dataset.py` (schema mirror + loader + structural validator),
  `ml/src/features.py` (byte-for-byte mirror of `PulseFeatureExtractor.kt`),
  `ml/src/sequences.py` (mirror of `TrainingSequenceBuilder.kt`),
  `ml/src/leakage_audit.py` (Phase 3 - **passes**, see `ml/reports/leakage_audit.md`),
  `ml/src/splitting.py`, `ml/src/models.py`, `ml/src/evaluation.py`,
  `ml/src/experiment_tracking.py`.
- `ml/scripts/{inspect_dataset,validate_dataset,build_sequences,train_baselines,
  train_cnn,evaluate_models,benchmark_models}.py` - all runnable, all exit with a clear
  "NO DATA" message (not a crash, not fabricated output) when pointed at a
  non-existent file.
- Architecture-level measurements that don't require training data (a model's byte size
  is a property of its architecture, not its trained weights) were genuinely measured:
  the Phase-7 CNN candidate is **7,538 parameters / ~35.2 KB FP32 / ~21.8 KB FP16 /
  ~15.2 KB INT8-dynamic-range** - see `ml/reports/` and the final report for caveats
  (dynamic-range INT8 only; full integer quantization needs a representative dataset we
  don't have).

## What is explicitly NOT done, and must not be assumed

- No model has been trained.
- No accuracy/precision/recall/F1/PR-AUC/ROC-AUC number has been produced for any
  classifier, baseline included - there is no data to compute one against.
- No feature-importance ranking, no calibration result, no threshold recommendation.
- No claim about whether real NetPulse telemetry CAN predict degradation/dropout - that
  question is unanswered, not answered-yes, until real data exists.

## Data collection target

Based on the label semantics (`degradation15s`: 15s look-ahead; `dropout30s`: 30s
look-ahead) and the session-grouped split strategy (`MIN_SESSIONS_FOR_HOLDOUT_SPLIT = 10`
in `ml/src/splitting.py`), before running `train_baselines.py`/`train_cnn.py`
meaningfully we need, as a **minimum viable** collection target:

| Target | Minimum | Rationale |
|---|---|---|
| Distinct sessions (post `NetworkSessionManager` grouping) | **>= 30** | `MIN_SESSIONS_FOR_HOLDOUT_SPLIT = 10` is the hard floor for *any* held-out split at all; 30 gives a 70/15/15 split roughly 21/4/5 sessions - still small, but enough to stop every split being 1-2 sessions. |
| RESOLVED `dropout30s` positive events | **>= 30**, spread across >= 10 distinct sessions | A handful of dropout events from one flaky router teaches the model that router, not "dropout" in general. PR-AUC on <10 positive test examples is close to meaningless. |
| RESOLVED `degradation15s` positive events | **>= 50** | Degradation is intended to be more common than full dropout; if it isn't showing up more often than dropout in real data, that's itself a finding worth reporting. |
| Transport diversity | Both **Wi-Fi and Cellular** represented with >= 5 sessions each | The 12-feature schema's RF fields are transport-specific (cellular RSRP/SINR vs Wi-Fi RSSI); a model trained on Wi-Fi-only data has no evidence it generalizes to cellular degradation. |
| Carrier / Wi-Fi network diversity | >= 3 distinct `networkIdHash` values | A single carrier/router's quirks (e.g. one ISP's bufferbloat profile) must not be mistaken for a general signature. |
| Time span | Collection spread over **>= 2 weeks**, not one sitting | Enables the Phase 11 chronological holdout to be meaningful at all - a single-session data-collection binge can't test "does this generalize to later behavior." |
| Device diversity | Not required for v1, but note it | Nice-to-have; a single test device's radio characteristics are a known confound RadarEngine can't fully abstract away. Track `Build.MODEL` in a future schema version if this becomes a blocker. |

None of the above numbers were invented to sound authoritative - they are derived directly
from `MIN_SESSIONS_FOR_HOLDOUT_SPLIT`, the requirement that a test set contain enough
positive examples for PR-AUC to be statistically meaningful (a common rule of thumb is
"don't trust a rate estimated from fewer than ~30 events"), and the transport/carrier
fields the feature schema already depends on.

## How to proceed once data exists

1. `python scripts/validate_dataset.py --input data/export.jsonl` - must PASS.
2. `python scripts/inspect_dataset.py --input data/export.jsonl --report reports/dataset_inspection.md`
   - compare the counts above against the collection target table.
3. If the target is met: `python scripts/build_sequences.py`, then
   `train_baselines.py`, then `train_cnn.py` for each variant, then
   `evaluate_models.py` to build the Phase 18 comparison table.
4. If the target is NOT met: keep collecting. Do not lower the bar by training on an
   undersized dataset and reporting the result as if it were reliable.

## Revisit this document

Every time `scripts/inspect_dataset.py` is run against a new export, compare its
"Dataset Size" / "Label Distribution" / "Session Distribution" sections against the
target table above and update this file's status line accordingly:
`NOT READY` -> `PROMISING BUT NEED MORE DATA` -> `READY`.
