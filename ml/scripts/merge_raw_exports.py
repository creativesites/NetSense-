#!/usr/bin/env python3
"""
Concatenates every file in data/raw_exports/ into one working dataset for the other
scripts to consume. Each raw export is a complete, self-consistent snapshot in its own
right (see raw_exports/README.md) - this just stacks their rows; it does not deduplicate
across files (session IDs are UUIDs, collisions across independent exports are not
expected in practice) or re-resolve labels (each row's labels were already resolved
on-device at export time).

Usage:
    python scripts/merge_raw_exports.py --output data/merged.jsonl
    python scripts/merge_raw_exports.py --raw-dir data/raw_exports --output data/merged.jsonl
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from src import dataset


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw-dir", default=str(Path(__file__).resolve().parent.parent / "data" / "raw_exports"))
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    raw_dir = Path(args.raw_dir)
    # Sorted by filename, which encodes collection time (YYYYMMDD_HHMM...) - so later
    # files sort last. This matters for deduplication below.
    files = sorted(raw_dir.glob("*.jsonl"))
    if not files:
        print(f"NO DATA: no .jsonl files found in {raw_dir}", file=sys.stderr)
        return 2

    frames = []
    for f in files:
        df = dataset.load_jsonl(f)
        frames.append(df)
        print(f"  {f.name}: {len(df)} rows, {df['sessionId'].nunique()} sessions")

    import pandas as pd

    # IMPORTANT: DatasetExportService.getProductionObservations() exports the app's ENTIRE
    # accumulated history every time, not just rows new since the last export. Two exports
    # from the same device will therefore massively overlap - a naive concatenation would
    # count the same real observation multiple times. Deduplicate on (sessionId, timestamp)
    # (a real observation's natural identity - the export doesn't include Room's row id),
    # keeping the LAST occurrence: later files sort last, and a label can only ever move
    # UNRESOLVED -> RESOLVED/INSUFFICIENT_DATA over time, never backwards, so the later
    # file's copy of a duplicate row is always at least as well-labeled as the earlier one.
    concatenated = pd.concat(frames, ignore_index=True)
    before = len(concatenated)
    merged = concatenated.drop_duplicates(subset=["sessionId", "timestamp"], keep="last").reset_index(drop=True)
    duplicates_dropped = before - len(merged)
    if duplicates_dropped:
        print(
            f"Deduplicated {duplicates_dropped} rows that appeared in more than one export "
            f"(same sessionId+timestamp) - kept the latest-labeled copy of each.",
        )

    issues = dataset.validate_schema(merged)
    errors = [i for i in issues if i.severity == "error"]
    if errors:
        print("MERGED DATASET FAILED VALIDATION:", file=sys.stderr)
        for i in errors:
            print(f"  ERROR: {i.message}", file=sys.stderr)
        return 3

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w") as out:
        for _, row in merged.iterrows():
            out.write(dataset_row_to_jsonl(row) + "\n")

    print(f"\nMerged {len(files)} files -> {len(merged)} rows, {merged['sessionId'].nunique()} sessions")
    print(f"Written to {output_path}")
    return 0


def dataset_row_to_jsonl(row) -> str:
    """Reconstructs one export-format JSONL line from a loaded (flattened) row - the
    inverse of dataset.load_jsonl's flattening, so merged.jsonl stays a valid input to
    every other script."""
    import json

    import pandas as pd

    def clean(v):
        if v is None:
            return None
        try:
            if pd.isna(v):
                return None
        except (TypeError, ValueError):
            pass
        if hasattr(v, "item"):
            return v.item()
        return v

    obj = {k: clean(row[k]) for k in row.index if not k.startswith("label")}
    obj["labels"] = {
        "degradation15s": clean(row["labelDegradation15s"]),
        "degradation15sStatus": clean(row["labelDegradation15sStatus"]),
        "dropout30s": clean(row["labelDropout30s"]),
        "dropout30sStatus": clean(row["labelDropout30sStatus"]),
        "likelyCause": clean(row["labelLikelyCause"]),
        "likelyCauseStatus": clean(row["labelLikelyCauseStatus"]),
        "schemaVersion": clean(row["labelSchemaVersion"]),
    }
    return json.dumps(obj)


if __name__ == "__main__":
    raise SystemExit(main())
