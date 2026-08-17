# ml/experiments/

Every training/evaluation run that produces a reportable metric writes one
`ExperimentRecord` JSON file here (see `src/experiment_tracking.py`), named
`<UTC timestamp>_<experiment name>.json`.

Each record captures (Phase 21 - reproducibility):

- `seed`
- `dataset_source_path`, `dataset_row_count`
- `feature_schema_version`, `label_schema_version`
- `model_architecture`, `hyperparameters`
- `split_strategy`, `train_sessions`, `val_sessions`, `test_sessions`
- `metrics` (full threshold sweep + PR-AUC/ROC-AUC + calibration where applicable)
- `model_param_count`, `model_size_bytes`

`scripts/evaluate_models.py` reads every `*.json` in this directory to build the Phase 18
model comparison table - it does not train anything itself.

## Git policy

`*.json` experiment records are `.gitignore`d by default (they can be large in aggregate
and are regenerated from `ml/data/` + the scripts, not hand-authored). Only this README and
`.gitkeep` are tracked. If a specific experiment result should be preserved as part of the
project's history, commit it deliberately with `git add -f`.

## Current status

Empty. No training has been run yet - see `../DATA_COLLECTION_STATUS.md`.
