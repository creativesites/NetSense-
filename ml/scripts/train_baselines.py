#!/usr/bin/env python3
"""
Phase 6: majority-class, rule-based, and logistic-regression baselines.

Usage:
    python scripts/train_baselines.py --sequences data/sequences.npz --task dropout30s

Trains on the TRAIN sessions, evaluates on the TEST sessions (session-grouped split, Phase
10) or, if there are too few distinct sessions for a meaningful holdout, runs grouped
cross-validation instead and says so explicitly rather than reporting a single, possibly
misleading split.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import numpy as np

from src import evaluation, experiment_tracking, models, splitting

TASK_KEYS = {
    "degradation15s": ("X_degradation15s", "y_degradation15s", "sessions_degradation15s"),
    "dropout30s": ("X_dropout30s", "y_dropout30s", "sessions_dropout30s"),
}


def run_for_split(X, y, sessions, split: splitting.SessionSplit, seed: int) -> dict:
    train_mask = np.array([s in set(split.train_sessions) for s in sessions])
    test_mask = np.array([s in set(split.test_sessions) for s in sessions])

    X_train, y_train = X[train_mask], y[train_mask]
    X_test, y_test = X[test_mask], y[test_mask]

    if len(set(y_test.tolist())) < 2:
        raise ValueError(
            f"Test split has only one class present ({set(y_test.tolist())}) - cannot "
            f"compute PR-AUC/ROC-AUC honestly. This usually means the dataset is too small "
            f"or too imbalanced for this split; see DATA_COLLECTION_STATUS.md."
        )

    results = {}

    majority = models.MajorityBaseline().fit(y_train)
    results["majority_baseline"] = _evaluate(majority.predict_proba(len(y_test)), y_test)

    rule = models.RuleBasedBaseline()
    results["rule_based_baseline"] = _evaluate(rule.predict_proba(X_test), y_test)

    logreg = models.build_logistic_regression()
    logreg.fit(models.flatten_windows(X_train), y_train)
    logreg_prob = logreg.predict_proba(models.flatten_windows(X_test))[:, 1]
    results["logistic_regression"] = _evaluate(logreg_prob, y_test)
    results["logistic_regression"]["param_count"] = int(logreg.coef_.size + logreg.intercept_.size)

    return results


def _evaluate(y_prob: np.ndarray, y_true: np.ndarray) -> dict:
    out = {"n_test": len(y_true)}
    out["pr_auc"] = evaluation.pr_auc(y_true, y_prob)
    out["roc_auc"] = evaluation.roc_auc(y_true, y_prob)
    out["threshold_sweep"] = [m.as_dict() for m in evaluation.threshold_sweep(y_true, y_prob)]
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sequences", required=True, help="Path to .npz from build_sequences.py")
    parser.add_argument("--task", choices=["degradation15s", "dropout30s"], required=True)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--experiments-dir", default=str(Path(__file__).resolve().parent.parent / "experiments"))
    args = parser.parse_args()

    seq_path = Path(args.sequences)
    if not seq_path.exists():
        print(
            f"NO DATA: {seq_path} does not exist. Run build_sequences.py against a real "
            f"dataset export first.",
            file=sys.stderr,
        )
        return 2

    data = np.load(seq_path, allow_pickle=True)
    X_key, y_key, sessions_key = TASK_KEYS[args.task]
    X, y, sessions = data[X_key], data[y_key], data[sessions_key].tolist()

    if len(y) == 0:
        print(f"NO DATA: 0 RESOLVED '{args.task}' sequences in {seq_path}.", file=sys.stderr)
        return 2

    unique_sessions = sorted(set(sessions))
    if len(unique_sessions) < splitting.MIN_SESSIONS_FOR_HOLDOUT_SPLIT:
        print(
            f"Only {len(unique_sessions)} distinct sessions have a RESOLVED '{args.task}' "
            f"label - below the minimum of {splitting.MIN_SESSIONS_FOR_HOLDOUT_SPLIT} needed "
            f"for a meaningful held-out train/val/test split. Not training - see "
            f"DATA_COLLECTION_STATUS.md for the data collection target.",
            file=sys.stderr,
        )
        return 3

    split = splitting.session_train_val_test_split(unique_sessions, seed=args.seed)
    results = run_for_split(X, y, sessions, split, args.seed)

    for model_name, metrics in results.items():
        print(f"\n=== {model_name} ===")
        print(f"  PR-AUC: {metrics['pr_auc']:.4f}")
        print(f"  ROC-AUC: {metrics['roc_auc']:.4f}")
        at_50 = next(m for m in metrics["threshold_sweep"] if m["threshold"] == 0.5)
        print(f"  @0.5 -> precision={at_50['precision']:.3f} recall={at_50['recall']:.3f} f1={at_50['f1']:.3f}")

        record = experiment_tracking.ExperimentRecord(
            name=f"baseline_{model_name}_{args.task}",
            seed=args.seed,
            dataset_source_path=str(seq_path),
            dataset_row_count=len(y),
            model_architecture=model_name,
            split_strategy="session_train_val_test_split(70/15/15)",
            train_sessions=len(split.train_sessions),
            val_sessions=len(split.val_sessions),
            test_sessions=len(split.test_sessions),
            metrics=metrics,
        )
        out_path = record.save(args.experiments_dir)
        print(f"  Experiment record: {out_path}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
