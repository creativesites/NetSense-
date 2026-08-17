#!/usr/bin/env python3
"""
Phase 18: builds the model comparison table from saved ExperimentRecord JSON files
(ml/experiments/*.json) - it does not train anything itself, only aggregates real,
already-produced results. If ml/experiments/ is empty, it says so and exits instead of
printing an empty table as if it were meaningful.

Usage:
    python scripts/evaluate_models.py --task dropout30s --threshold 0.5
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


def load_records(experiments_dir: Path) -> list[dict]:
    records = []
    for path in sorted(experiments_dir.glob("*.json")):
        try:
            records.append(json.loads(path.read_text()))
        except json.JSONDecodeError:
            print(f"WARNING: could not parse {path}, skipping", file=sys.stderr)
    return records


def metrics_for_task(record: dict, task: str) -> dict | None:
    metrics = record.get("metrics", {})
    # Baseline records store metrics per-model directly; CNN/MLP records store metrics per-task.
    if task in metrics:
        return metrics[task]
    return None


def find_threshold_row(task_metrics: dict, threshold: float) -> dict | None:
    for row in task_metrics.get("threshold_sweep", []):
        if abs(row["threshold"] - threshold) < 1e-9:
            return row
    return None


def build_table(records: list[dict], task: str, threshold: float) -> str:
    lines = [
        f"# Model Comparison - task={task}, threshold={threshold}",
        "",
        "| Model | Parameters | Precision | Recall | F1 | PR-AUC | FPR | FNR | Notes |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    any_rows = False
    for record in records:
        task_metrics = metrics_for_task(record, task)
        if task_metrics is None:
            continue
        row = find_threshold_row(task_metrics, threshold)
        if row is None:
            continue
        any_rows = True
        params = record.get("model_param_count")
        if params is None:
            # baselines store per-model param counts inside the metrics dict itself
            params = task_metrics.get("param_count", "n/a")
        lines.append(
            f"| {record['name']} | {params} | {row['precision']:.3f} | {row['recall']:.3f} | "
            f"{row['f1']:.3f} | {task_metrics.get('pr_auc', float('nan')):.3f} | "
            f"{row['false_positive_rate']:.3f} | {row['false_negative_rate']:.3f} | "
            f"seed={record.get('seed')}, test_n={row['n']} |"
        )
    if not any_rows:
        lines.append("| (no experiment records found for this task/threshold) | | | | | | | | |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--experiments-dir", default=str(Path(__file__).resolve().parent.parent / "experiments"))
    parser.add_argument("--task", choices=["degradation15s", "dropout30s"], required=True)
    parser.add_argument("--threshold", type=float, default=0.5)
    parser.add_argument("--report", default=None)
    args = parser.parse_args()

    experiments_dir = Path(args.experiments_dir)
    records = load_records(experiments_dir)
    if not records:
        print(
            f"NO EXPERIMENT RECORDS found in {experiments_dir}. Nothing has been trained "
            f"yet - run train_baselines.py / train_cnn.py against a real dataset first.",
            file=sys.stderr,
        )
        return 2

    table = build_table(records, args.task, args.threshold)
    print(table)

    if args.report:
        report_path = Path(args.report)
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(table)
        print(f"Report written to {report_path}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
