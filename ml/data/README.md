# ml/data/

Real NetPulse dataset exports live here (never fabricated data).

```
data/
  raw_exports/     <- every export, as received, permanently, one file per upload
                       (see raw_exports/README.md for the naming convention)
  export.jsonl     <- optional: a copy/symlink of the current "working" file, if you
                       want scripts/*.py --input to point at a stable name instead of
                       repeating the timestamped filename every time
```

## How to send a new export

1. Run the NetPulse Android app long enough to accumulate telemetry across multiple
   sessions, transports, and (ideally) some real degradation/dropout events.
2. In the app: **Analytics tab → "Export ML Training Dataset (JSONL)"**
   (`NetPulseViewModel.exportMlDataset`, `DatasetExportService`). This exports only the
   PRODUCTION (non-synthetic) dataset.
3. **Attach the exported file directly to a message in this conversation** (the same way
   you'd send any file to Claude - drag-and-drop or the attach button). There's no
   filesystem path on your phone/computer that reaches this repository directly; the
   attachment is how a file actually gets here. Once attached, it gets saved into
   `data/raw_exports/` with a clear name and you don't need to do anything else.
4. Point the scripts at whichever file(s) matter:
   ```bash
   python scripts/validate_dataset.py --input data/raw_exports/<file>.jsonl
   python scripts/inspect_dataset.py --input data/raw_exports/<file>.jsonl \
       --report reports/dataset_inspection.md
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
