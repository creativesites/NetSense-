# NetPulse PulsePredictor Model Lab

Offline ML experimentation environment for PulsePredictor. This directory is intentionally
**disconnected from the Android app build** (Phase 24 production boundary):

```
Android (Kotlin):  Telemetry -> Room -> Label Resolution -> Dataset Export
                                                                  |
                                                    (a real .csv/.jsonl file)
                                                                  v
Offline ML (this dir):  Dataset -> preprocessing -> training -> evaluation
                          -> model selection -> quantization -> exported artifact
                                                                  |
                                                     (a future .tflite file,
                                                      NOT produced by this phase)
                                                                  v
Future Android:    pulse_predictor_v1.tflite -> PulsePredictorEngine -> PulsePrediction
```

**Current status: [NOT READY](DATA_COLLECTION_STATUS.md) - no real dataset exists yet.**
Read that file first. Everything below describes tooling that is built, tested, and
waiting for real data - not results that already exist.

## Setup

```bash
cd ml
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Layout

```
ml/
  README.md                    this file
  DATA_COLLECTION_STATUS.md    Phase 23 status - READ THIS FIRST
  requirements.txt
  pytest.ini
  data/                        put a real dataset export here (gitignored)
  scripts/                     CLI entry points, one per pipeline stage
    inspect_dataset.py           Phase 2  - dataset quality report
    validate_dataset.py          Phase 1/3/4 - schema + leakage + temporal validation
    build_sequences.py           windows a dataset export into model-ready [15,12] sequences
    train_baselines.py           Phase 6  - majority / rule-based / logistic regression
    train_cnn.py                 Phase 7/8 - MLP + CNN, single-task and multi-task variants
    evaluate_models.py           Phase 18 - model comparison table from saved experiments
    benchmark_models.py          Phase 12/13/14 - size, quantization, Android benchmark spec
  src/                          the actual logic, unit-tested, imported by scripts/
    dataset.py                   export schema mirror, CSV/JSONL loader, validator
    features.py                  mirror of PulseFeatureExtractor.kt (must stay byte-identical)
    sequences.py                 mirror of TrainingSequenceBuilder.kt
    leakage_audit.py              Phase 3 feature leakage audit + runtime guard
    splitting.py                  Phase 10/11 session-based + chronological splitting
    models.py                     Phase 6/7/8 baseline + CNN model definitions
    evaluation.py                 Phase 9/15/16/17 metrics, thresholds, calibration, importance
    experiment_tracking.py        Phase 21 reproducible experiment records
  experiments/                  ExperimentRecord JSON output (gitignored, regenerable)
  reports/                      generated markdown reports (mostly gitignored, see reports/README.md)
  tests/                        pytest suite - synthetic FIXTURES ONLY, never real data
```

## Why Python mirrors Kotlin code by hand

`src/features.py` and `src/sequences.py` are deliberate, careful re-implementations of
`PulseFeatureExtractor.kt` and `TrainingSequenceBuilder.kt`, not generated bindings. This
is intentional: a model can only be trusted at Android inference time if the offline
training features are IDENTICAL to what `PulsePredictorEngine` actually feeds a TFLite
model on-device. If the Kotlin extractor changes, `ml/src/features.py` and
`FEATURE_SCHEMA_VERSION` must change in the same commit - `tests/test_features.py` is the
guard rail that catches drift on the Python side; there is no automated cross-language
check (yet) that catches drift on the Kotlin side, so review both files together.

## Running the tests

```bash
source .venv/bin/activate
python -m pytest tests/ -v
```

These tests use small, explicitly-labeled synthetic fixtures (`tests/fixtures.py`) to
prove the CODE is correct. They are never a substitute for, and must never be cited as,
a real dataset inspection or model result - see `DATA_COLLECTION_STATUS.md`.

## Typical workflow once a real export exists

```bash
# 1. Get a real export into data/ - see data/README.md
cp ~/Downloads/netpulse_ml_dataset_*.jsonl data/export.jsonl

# 2. Validate structure, leakage, temporal cadence - must pass before anything else
python scripts/validate_dataset.py --input data/export.jsonl

# 3. Inspect dataset quality - compare against DATA_COLLECTION_STATUS.md's targets
python scripts/inspect_dataset.py --input data/export.jsonl --report reports/dataset_inspection.md

# 4. Build windowed training sequences
python scripts/build_sequences.py --input data/export.jsonl --output data/sequences.npz

# 5. Baselines (majority / rule-based / logistic regression)
python scripts/train_baselines.py --sequences data/sequences.npz --task dropout30s
python scripts/train_baselines.py --sequences data/sequences.npz --task degradation15s

# 6. Neural candidates
python scripts/train_cnn.py --sequences data/sequences.npz --variant mlp_dropout30s
python scripts/train_cnn.py --sequences data/sequences.npz --variant cnn_dropout30s_only
python scripts/train_cnn.py --sequences data/sequences.npz --variant cnn_degradation15s_only
python scripts/train_cnn.py --sequences data/sequences.npz --variant cnn_multi_task

# 7. Compare everything
python scripts/evaluate_models.py --task dropout30s --report reports/model_comparison_dropout30s.md

# 8. Size/quantization for the winner
python scripts/benchmark_models.py --variant cnn_multi_task
```

Only after this full loop runs against REAL data, and the results are judged trustworthy
(Phase 19), does the next phase (model freeze -> TFLite conversion -> Android integration)
begin. This lab does not do that conversion or integration.
