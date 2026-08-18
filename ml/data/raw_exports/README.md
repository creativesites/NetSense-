# ml/data/raw_exports/

**This is the drop point for every real NetPulse export as it arrives.** Append-only:
files here are never edited or overwritten, so the raw collection history stays intact
even as later steps (merging, filtering, relabeling) evolve.

## Naming convention

```
<YYYYMMDD>_<HHMM>_<short-device-or-carrier-tag>_<seq>.jsonl
```

Examples:

```
20260818_0827_s20-airtel-zm_001.jsonl   # exported ~08:27 local, Samsung S20, Airtel Zambia
20260819_1910_s20-airtel-zm_002.jsonl   # next day's export from the same device
```

The tag doesn't need to be precise - its only job is to keep multiple files visually
distinct at a glance (different device, different carrier, different collection day).
Whatever you send, it gets a name like this before landing here; you don't need to name
it yourself.

## Why raw exports are never merged in place

- `NetworkSessionManager` mints a fresh `sessionId` per contiguous network context, but
  session IDs are only unique *within* one export - two separate exports could
  (extremely unlikely, but not impossible) collide on a UUID, and more importantly each
  export is a complete, self-consistent snapshot from `getProductionObservations()` at
  the moment it was taken. Concatenating them naively risks re-processing rows that
  already appear in an earlier export.
- Keeping every original file untouched means any question about "what did the raw data
  actually say" can always be answered by going back to the source, rather than trusting
  a merge step that already happened.

## How this feeds the pipeline

Once there's more than one file here, combine them into a single working dataset before
running the pipeline (a small `scripts/merge_raw_exports.py` will be added once we
actually need it - not built yet, since there's only one file to merge so far):

```bash
# for now, with a single file:
python scripts/validate_dataset.py --input data/raw_exports/<the file>.jsonl
python scripts/inspect_dataset.py --input data/raw_exports/<the file>.jsonl \
    --report reports/dataset_inspection.md
```

## Git policy

Everything here except this README and `.gitkeep` is `.gitignore`d
(`ml/data/**/*.jsonl`, `**/*.csv`, `**/*.db`) - see `../README.md` for why real telemetry
isn't committed by default.
