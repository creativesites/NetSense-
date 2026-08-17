# ml/data/

Place a real NetPulse dataset export here (never fabricated data).

## How to get a real export

1. Run the NetPulse Android app on a device (or emulator with real network conditions)
   long enough to accumulate telemetry across multiple sessions, transports, and (ideally)
   some real degradation/dropout events.
2. In the app: **Settings/Analytics tab → "Export ML Training Dataset (JSONL)"**
   (`NetPulseViewModel.exportMlDataset`, `DatasetExportService`). This exports only the
   PRODUCTION (non-synthetic) dataset - see `com.netsense.netpulse.dataset.DatasetExportService`.
3. Pull the shared file off the device and place it here, e.g. `ml/data/export_2026xxxx.jsonl`.
4. Point the scripts at it:
   ```bash
   python scripts/validate_dataset.py --input data/export_2026xxxx.jsonl
   python scripts/inspect_dataset.py --input data/export_2026xxxx.jsonl --report reports/dataset_inspection.md
   ```

## What NOT to put here

- Synthetic/fabricated telemetry. If you need to smoke-test the pipeline, use
  `ml/tests/fixtures.py` (already covered by `pytest`), not a fake file in this directory.
- Anything containing secrets or API keys - NetPulse exports never include these, but this
  directory is `.gitignore`d as a defense-in-depth measure regardless.

## Git policy

Everything in this directory except this README and `.gitkeep` is `.gitignore`d
(`ml/data/*.csv`, `*.jsonl`, `*.db`). Real telemetry - even pseudonymized - should not be
committed to a shared repository by default; see `DatasetExportService`'s privacy notes
for what is/isn't included in an export.
