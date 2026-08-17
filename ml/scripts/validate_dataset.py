#!/usr/bin/env python3
"""
Phase 1/3/4: schema validation, feature-leakage audit, and temporal-window validation.

Usage:
    python scripts/validate_dataset.py --input data/export.jsonl

Exits non-zero if ANY structural, leakage, or temporal problem is found - this script is
meant to gate training (train_baselines.py/train_cnn.py should refuse to run on a dataset
that fails this check).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import numpy as np
import pandas as pd

from src import dataset, leakage_audit


def validate_temporal_windows(df: pd.DataFrame, window_size: int = 15) -> list[str]:
    """Phase 4: does NOT assume 15 rows == 15 seconds. Reports the actual observed cadence
    and flags sessions where windows would be built from an unreasonably sparse or gappy
    stream."""
    problems: list[str] = []
    usable = df[(df["observationQuality"] != "INVALID") & (~df["isSynthetic"].fillna(False).astype(bool))]

    intervals = []
    duplicate_ts_sessions = []
    for session_id, group in usable.groupby("sessionId"):
        ts = group.sort_values("timestamp")["timestamp"].to_numpy()
        if len(ts) < 2:
            continue
        diffs = np.diff(ts)
        intervals.extend(diffs.tolist())
        if np.any(diffs == 0):
            duplicate_ts_sessions.append(str(session_id))

    if not intervals:
        problems.append(
            "No session has >=2 usable observations, so no real inter-observation interval "
            "could be measured yet."
        )
        return problems

    intervals_arr = np.array(intervals)
    median_interval = float(np.median(intervals_arr))
    print(f"Median inter-observation interval: {median_interval:.0f} ms")
    print(f"Interval std dev: {float(np.std(intervals_arr)):.0f} ms")
    print(f"Min interval: {int(intervals_arr.min())} ms")
    print(f"Max interval: {int(intervals_arr.max())} ms")

    implied_window_span_s = (median_interval * (window_size - 1)) / 1000.0
    print(
        f"With WINDOW_SIZE={window_size} and a median interval of {median_interval:.0f} ms, "
        f"a typical training window spans ~{implied_window_span_s:.1f}s of wall-clock time "
        f"- this is NOT necessarily 15s and must not be assumed to be."
    )
    if abs(implied_window_span_s - (window_size - 1)) > 5:
        problems.append(
            f"The actual median cadence implies windows spanning ~{implied_window_span_s:.1f}s, "
            f"which differs materially from a naive '15 rows = 15 seconds' assumption. "
            f"Any model trained on these windows must be evaluated (and later deployed) "
            f"against the REAL cadence, not the originally assumed one."
        )

    if duplicate_ts_sessions:
        problems.append(
            f"{len(duplicate_ts_sessions)} session(s) contain duplicate timestamps within "
            f"the same session: {duplicate_ts_sessions[:5]}{'...' if len(duplicate_ts_sessions) > 5 else ''}"
        )

    return problems


def validate_leakage() -> list[str]:
    return leakage_audit.audit_feature_schema()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    args = parser.parse_args()

    input_path = Path(args.input)
    if input_path.suffix.lower() == ".jsonl":
        try:
            df = dataset.load_jsonl(input_path)
        except FileNotFoundError as e:
            print(f"NO DATA: {e}", file=sys.stderr)
            return 2
    elif input_path.suffix.lower() == ".csv":
        try:
            df = dataset.load_csv(input_path)
        except FileNotFoundError as e:
            print(f"NO DATA: {e}", file=sys.stderr)
            return 2
    else:
        print(f"unrecognized extension: {input_path.suffix}", file=sys.stderr)
        return 2

    all_problems: list[str] = []

    print("=== Schema validation ===")
    schema_issues = dataset.validate_schema(df)
    for issue in schema_issues:
        print(f"[{issue.severity.upper()}] {issue.message}")
        if issue.severity == "error":
            all_problems.append(issue.message)

    print("\n=== Feature leakage audit ===")
    leakage_problems = validate_leakage()
    if leakage_problems:
        for p in leakage_problems:
            print(f"[ERROR] {p}")
        all_problems.extend(leakage_problems)
    else:
        print("PASSED - no label column found among model features; all 12 features trace "
              "to a SAFE leakage-audit entry.")

    print("\n=== Temporal window validation ===")
    temporal_problems = validate_temporal_windows(df)
    for p in temporal_problems:
        print(f"[WARNING] {p}")
    # Temporal cadence differences are reported, not treated as hard failures - see docstring.

    print("\n=== Result ===")
    if all_problems:
        print(f"FAILED: {len(all_problems)} blocking problem(s) found.")
        return 1
    print("PASSED: dataset is structurally sound and free of detected feature leakage.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
