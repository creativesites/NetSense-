# ml/reports/

Generated markdown reports land here:

- `dataset_inspection_*.md` - from `scripts/inspect_dataset.py` (Phase 2).
- `model_comparison_*.md` - from `scripts/evaluate_models.py` (Phase 18).
- `leakage_audit.md` - from `src/leakage_audit.py:render_leakage_report()` (Phase 3; this
  one is dataset-independent and can be regenerated any time).

## Git policy

Dataset-dependent reports (`dataset_inspection_*.md`, `model_comparison_*.md`) are
`.gitignore`d - they're derived from `ml/data/` and regenerable, and may summarize
statistics about real (if pseudonymized) network telemetry. The dataset-INDEPENDENT
leakage audit is safe to commit since it describes only the code/schema, never a dataset.

## Current status

Empty - see `../DATA_COLLECTION_STATUS.md` for why no dataset-dependent report exists yet.
