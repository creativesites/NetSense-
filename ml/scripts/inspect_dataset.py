#!/usr/bin/env python3
"""
Phase 2: dataset quality report.

Usage:
    python scripts/inspect_dataset.py --input data/export.jsonl [--report reports/dataset_inspection.md]

Never fabricates numbers: if --input doesn't exist, it exits with a clear message instead
of printing a report for an empty/synthetic dataset.
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import numpy as np
import pandas as pd

from src import dataset, features, sequences

RAW_FEATURE_COLUMNS = {
    "dns_latency_ms": ("dnsLatencyMs", features.DNS_LATENCY_MIN_MS, features.DNS_LATENCY_MAX_MS),
    "tcp_rtt_ms": ("tcpRttMs", features.TCP_RTT_MIN_MS, features.TCP_RTT_MAX_MS),
    "tcp_jitter_ms": ("tcpJitterMs", features.TCP_JITTER_MIN_MS, features.TCP_JITTER_MAX_MS),
    "http_ttfb_ms": ("httpTtfbMs", features.HTTP_TTFB_MIN_MS, features.HTTP_TTFB_MAX_MS),
    "packet_loss_pct": ("packetLossPct", 0.0, 1.0),
    "rsrp_dbm": ("rsrpDbm", features.RSRP_MIN_DBM, features.RSRP_MAX_DBM),
    "sinr_db": ("sinrDb", features.SINR_MIN_DB, features.SINR_MAX_DB),
    "wifi_rssi_dbm": ("wifiRssiDbm", features.WIFI_RSSI_MIN_DBM, features.WIFI_RSSI_MAX_DBM),
    "consecutive_probe_failures": ("consecutiveProbeFailures", 0.0, 5.0),
}


def load(input_path: Path) -> pd.DataFrame:
    if input_path.suffix.lower() == ".jsonl":
        return dataset.load_jsonl(input_path)
    if input_path.suffix.lower() == ".csv":
        return dataset.load_csv(input_path)
    raise ValueError(f"unrecognized dataset file extension: {input_path.suffix} (expected .csv or .jsonl)")


def section_dataset_size(df: pd.DataFrame, seqs: list) -> list[str]:
    quality_counts = df["observationQuality"].value_counts(dropna=False).to_dict()
    lines = [
        "## Dataset Size",
        f"- Total observations: {len(df)}",
        f"- VALID: {quality_counts.get('VALID', 0)}",
        f"- PARTIAL: {quality_counts.get('PARTIAL', 0)}",
        f"- INVALID: {quality_counts.get('INVALID', 0)}",
        f"- SYNTHETIC: {quality_counts.get('SYNTHETIC', 0)}",
        f"- Distinct sessions: {df['sessionId'].nunique()}",
        f"- Training sequences (WINDOW_SIZE={features.WINDOW_SIZE}, session-safe): {len(seqs)}",
    ]
    return lines


def section_label_distribution(df: pd.DataFrame) -> list[str]:
    lines = ["## Label Distribution"]
    for label_col, status_col, title in (
        ("labelDegradation15s", "labelDegradation15sStatus", "degradation15s"),
        ("labelDropout30s", "labelDropout30sStatus", "dropout30s"),
    ):
        status_counts = df[status_col].value_counts(dropna=False).to_dict()
        resolved = df[df[status_col] == "RESOLVED"]
        pos = int((resolved[label_col] == True).sum())  # noqa: E712
        neg = int((resolved[label_col] == False).sum())  # noqa: E712
        resolved_n = pos + neg
        pos_pct = (pos / resolved_n * 100) if resolved_n else float("nan")
        lines += [
            f"### {title}",
            f"- RESOLVED positive: {pos}",
            f"- RESOLVED negative: {neg}",
            f"- UNRESOLVED: {status_counts.get('UNRESOLVED', 0)}",
            f"- INSUFFICIENT_DATA: {status_counts.get('INSUFFICIENT_DATA', 0)}",
            f"- Positive percentage (of RESOLVED): {pos_pct:.2f}%" if resolved_n else "- Positive percentage: N/A (0 resolved rows)",
        ]
    return lines


def section_likely_cause(df: pd.DataFrame) -> list[str]:
    lines = ["## Likely Cause Distribution"]
    counts = df["labelLikelyCause"].value_counts(dropna=False)
    unlabeled = int(df["labelLikelyCauseStatus"].eq("UNLABELED").sum())
    for cause, count in counts.items():
        label = cause if pd.notna(cause) else "(null)"
        lines.append(f"- {label}: {int(count)}")
    lines.append(f"- UNLABELED (status): {unlabeled}")
    return lines


def section_session_distribution(df: pd.DataFrame, seqs: list) -> list[str]:
    lines = ["## Session Distribution"]
    durations = df.groupby("sessionId")["timestamp"].agg(lambda s: s.max() - s.min())
    obs_per_session = df.groupby("sessionId").size()
    seq_per_session = pd.Series([s.session_id for s in seqs]).value_counts() if seqs else pd.Series(dtype=int)

    if len(durations) == 0:
        lines.append("- No sessions present.")
        return lines

    lines += [
        f"- Min session duration: {durations.min()} ms",
        f"- Median session duration: {durations.median():.0f} ms",
        f"- Max session duration: {durations.max()} ms",
        f"- Min observations/session: {obs_per_session.min()}",
        f"- Median observations/session: {obs_per_session.median():.1f}",
        f"- Max observations/session: {obs_per_session.max()}",
        f"- Sessions producing >=1 training sequence: {seq_per_session.shape[0]} / {len(durations)}",
        f"- Median sequences/session (sessions with >=1): {seq_per_session.median():.1f}" if len(seq_per_session) else "- Median sequences/session: N/A",
    ]
    return lines


def section_feature_distributions(df: pd.DataFrame) -> list[str]:
    lines = ["## Feature Distributions (raw units, VALID+PARTIAL non-synthetic rows only)"]
    usable = df[(df["observationQuality"] != "INVALID") & (~df["isSynthetic"].fillna(False).astype(bool))]

    for feature_name, (col, lo, hi) in RAW_FEATURE_COLUMNS.items():
        series = usable[col]
        missing_pct = series.isna().mean() * 100
        present = series.dropna()
        lines.append(f"### {feature_name} ({col})")
        if len(present) == 0:
            lines.append("- No non-missing values present.")
            continue
        lines += [
            f"- min: {present.min():.3g}",
            f"- max: {present.max():.3g}",
            f"- mean: {present.mean():.3g}",
            f"- median: {present.median():.3g}",
            f"- std: {present.std():.3g}",
            f"- missing: {missing_pct:.2f}%",
        ]
        out_of_range = present[(present < lo) | (present > hi)]
        if len(out_of_range) > 0:
            lines.append(f"- FLAG: {len(out_of_range)} value(s) outside the plausible range [{lo}, {hi}]")
        if present.std() == 0:
            lines.append("- FLAG: constant feature (zero variance) - provides no signal to a model")
        elif present.mean() != 0 and abs(present.std() / present.mean()) < 0.01:
            lines.append("- FLAG: near-constant feature (coefficient of variation < 1%)")

    for col in ("transport",):
        counts = usable[col].value_counts(dropna=False)
        lines.append(f"### {col}")
        for val, count in counts.items():
            lines.append(f"- {val}: {int(count)}")
        if len(counts) <= 1:
            lines.append("- FLAG: constant categorical feature")

    return lines


def build_report(df: pd.DataFrame, seqs: list, source: Path) -> str:
    header = [
        f"# NetPulse Dataset Inspection Report",
        f"",
        f"- Source file: `{source}`",
        f"- Generated: {datetime.now(timezone.utc).isoformat()}",
        f"- Feature schema version(s) present: {sorted(df['featureSchemaVersion'].dropna().unique().tolist())}",
        f"- Label schema version(s) present: {sorted(df['labelSchemaVersion'].dropna().unique().tolist())}",
        f"",
    ]
    body = (
        section_dataset_size(df, seqs)
        + [""] + section_label_distribution(df)
        + [""] + section_likely_cause(df)
        + [""] + section_session_distribution(df, seqs)
        + [""] + section_feature_distributions(df)
    )
    return "\n".join(header + body) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, help="Path to a real DatasetExportService CSV/JSONL export")
    parser.add_argument("--report", default=None, help="Optional path to write the markdown report")
    args = parser.parse_args()

    input_path = Path(args.input)
    try:
        df = load(input_path)
    except FileNotFoundError as e:
        print(f"NO DATA: {e}", file=sys.stderr)
        return 2

    issues = dataset.validate_schema(df)
    errors = [i for i in issues if i.severity == "error"]
    if errors:
        print("SCHEMA VALIDATION FAILED:", file=sys.stderr)
        for i in errors:
            print(f"  ERROR: {i.message}", file=sys.stderr)
        return 3

    seqs = sequences.build_sequences(df)
    report = build_report(df, seqs, input_path)
    print(report)

    if args.report:
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(report)
        print(f"\nReport written to {report_path}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
