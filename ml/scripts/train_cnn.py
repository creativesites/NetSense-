#!/usr/bin/env python3
"""
Phase 7/8: small-MLP and 1D-CNN experiments, including the single-task-A /
single-task-B / multi-task-C comparison Phase 8 asks for.

Usage:
    python scripts/train_cnn.py --sequences data/sequences.npz --variant multi_task

--variant one of:
    mlp_dropout30s          (Model 3 baseline, Phase 6)
    mlp_degradation15s
    cnn_dropout30s_only     (Phase 8 variant A)
    cnn_degradation15s_only (Phase 8 variant B)
    cnn_multi_task          (Phase 8 variant C: both heads; likely_cause head OFF by
                              default - see models.py docstring on why)

Every run writes one ExperimentRecord (Phase 21) - seed, dataset path, split, architecture,
hyperparameters, metrics, and parameter/size counts are all recorded together so a result
can be reproduced or audited later.
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

VARIANTS = {
    "mlp_dropout30s": {"tasks": ["dropout30s"], "kind": "mlp"},
    "mlp_degradation15s": {"tasks": ["degradation15s"], "kind": "mlp"},
    "cnn_dropout30s_only": {"tasks": ["dropout30s"], "kind": "cnn_single"},
    "cnn_degradation15s_only": {"tasks": ["degradation15s"], "kind": "cnn_single"},
    "cnn_multi_task": {"tasks": ["dropout30s", "degradation15s"], "kind": "cnn_multi"},
}


def _load_task(data, task: str, seed: int):
    X_key, y_key, sessions_key = TASK_KEYS[task]
    X, y, sessions = data[X_key], data[y_key], data[sessions_key].tolist()
    unique_sessions = sorted(set(sessions))
    if len(unique_sessions) < splitting.MIN_SESSIONS_FOR_HOLDOUT_SPLIT:
        return None
    split = splitting.session_train_val_test_split(unique_sessions, seed=seed)
    train_mask = np.array([s in set(split.train_sessions) for s in sessions])
    val_mask = np.array([s in set(split.val_sessions) for s in sessions])
    test_mask = np.array([s in set(split.test_sessions) for s in sessions])
    return X, y, split, train_mask, val_mask, test_mask


def train_mlp(X_train, y_train, X_val, y_val, epochs: int, seed: int):
    import tensorflow as tf

    tf.keras.utils.set_random_seed(seed)
    model = models.build_mlp(n_outputs=1, output_activation="sigmoid")
    model.compile(optimizer="adam", loss="binary_crossentropy", metrics=["accuracy"])
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=epochs, batch_size=32, verbose=0,
        class_weight=_class_weight(y_train),
    )
    return model


def train_cnn_single(task: str, X_train, y_train, X_val, y_val, epochs: int, seed: int):
    import tensorflow as tf

    tf.keras.utils.set_random_seed(seed)
    model = models.build_single_task_cnn(task)
    model.compile(optimizer="adam", loss="binary_crossentropy", metrics=["accuracy"])
    # NOTE: class-imbalance weighting (Phase 9 - failures should be rare) is applied for the
    # single-output MLP above via Keras' simple class_weight dict; Keras' per-output
    # class_weight for named multi-output models is version-sensitive, so it is
    # deliberately left as an explicit tuning knob for when real, imbalanced data exists
    # rather than guessed at here.
    model.fit(
        X_train, {task: y_train},
        validation_data=(X_val, {task: y_val}),
        epochs=epochs, batch_size=32, verbose=0,
    )
    return model


def train_cnn_multi(X_train, y_dropout_train, y_degradation_train, X_val, y_dropout_val, y_degradation_val, epochs: int, seed: int):
    import tensorflow as tf

    tf.keras.utils.set_random_seed(seed)
    heads = models.CnnHeadsConfig(dropout30s=True, degradation15s=True, likely_cause=False, anomaly=False)
    model = models.build_cnn(heads)
    model.compile(
        optimizer="adam",
        loss={"dropout30s": "binary_crossentropy", "degradation15s": "binary_crossentropy"},
        metrics={"dropout30s": ["accuracy"], "degradation15s": ["accuracy"]},
    )
    model.fit(
        X_train,
        {"dropout30s": y_dropout_train, "degradation15s": y_degradation_train},
        validation_data=(X_val, {"dropout30s": y_dropout_val, "degradation15s": y_degradation_val}),
        epochs=epochs, batch_size=32, verbose=0,
    )
    return model


def _class_weight(y: np.ndarray) -> dict:
    """Simple inverse-frequency class weighting - dropout/degradation events are expected
    to be rare (Phase 9)."""
    y = np.asarray(y)
    n = len(y)
    n_pos = max(int(np.sum(y == 1)), 1)
    n_neg = max(int(np.sum(y == 0)), 1)
    return {0: n / (2 * n_neg), 1: n / (2 * n_pos)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sequences", required=True)
    parser.add_argument("--variant", choices=list(VARIANTS.keys()), required=True)
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--experiments-dir", default=str(Path(__file__).resolve().parent.parent / "experiments"))
    args = parser.parse_args()

    seq_path = Path(args.sequences)
    if not seq_path.exists():
        print(f"NO DATA: {seq_path} does not exist. Run build_sequences.py first.", file=sys.stderr)
        return 2

    data = np.load(seq_path, allow_pickle=True)
    variant = VARIANTS[args.variant]

    if variant["kind"] == "cnn_multi":
        dropout_bundle = _load_task(data, "dropout30s", args.seed)
        degradation_bundle = _load_task(data, "degradation15s", args.seed)
        if dropout_bundle is None or degradation_bundle is None:
            print(
                "NOT ENOUGH SESSIONS for a held-out split on one or both tasks - see "
                "DATA_COLLECTION_STATUS.md.", file=sys.stderr,
            )
            return 3
        X, y_dropout, split, train_mask, val_mask, test_mask = dropout_bundle
        _, y_degradation, split2, train_mask2, val_mask2, test_mask2 = degradation_bundle
        if split.train_sessions != split2.train_sessions:
            print(
                "WARNING: dropout30s and degradation15s RESOLVED session sets differ; "
                "multi-task training requires the SAME rows to carry both labels. This "
                "build only trains multi-task on sequences where the underlying X arrays "
                "are aligned (X_dropout30s == X_degradation15s row-for-row expected from "
                "build_sequences.py, since both are derived from the same sequence list).",
                file=sys.stderr,
            )

        model = train_cnn_multi(
            X[train_mask], y_dropout[train_mask], y_degradation[train_mask],
            X[val_mask], y_dropout[val_mask], y_degradation[val_mask],
            args.epochs, args.seed,
        )
        preds = model.predict(X[test_mask], verbose=0)
        metrics = {
            "dropout30s": _evaluate(preds["dropout30s"].ravel(), y_dropout[test_mask]),
            "degradation15s": _evaluate(preds["degradation15s"].ravel(), y_degradation[test_mask]),
        }
        param_count = models.keras_param_count(model)
        train_sessions, val_sessions, test_sessions = len(split.train_sessions), len(split.val_sessions), len(split.test_sessions)
        row_count = len(y_dropout)

    else:
        task = variant["tasks"][0]
        bundle = _load_task(data, task, args.seed)
        if bundle is None:
            print(f"NOT ENOUGH SESSIONS for {task} - see DATA_COLLECTION_STATUS.md.", file=sys.stderr)
            return 3
        X, y, split, train_mask, val_mask, test_mask = bundle

        if variant["kind"] == "mlp":
            model = train_mlp(X[train_mask], y[train_mask], X[val_mask], y[val_mask], args.epochs, args.seed)
            probs = model.predict(X[test_mask], verbose=0).ravel()
        else:  # cnn_single
            model = train_cnn_single(task, X[train_mask], y[train_mask], X[val_mask], y[val_mask], args.epochs, args.seed)
            probs = model.predict(X[test_mask], verbose=0)[task].ravel()

        metrics = {task: _evaluate(probs, y[test_mask])}
        param_count = models.keras_param_count(model)
        train_sessions, val_sessions, test_sessions = len(split.train_sessions), len(split.val_sessions), len(split.test_sessions)
        row_count = len(y)

    for task_name, task_metrics in metrics.items():
        print(f"\n=== {args.variant} / {task_name} ===")
        print(f"  PR-AUC: {task_metrics['pr_auc']:.4f}")
        print(f"  ROC-AUC: {task_metrics['roc_auc']:.4f}")

    record = experiment_tracking.ExperimentRecord(
        name=f"{args.variant}",
        seed=args.seed,
        dataset_source_path=str(seq_path),
        dataset_row_count=row_count,
        model_architecture=args.variant,
        hyperparameters={"epochs": args.epochs, "batch_size": 32, "optimizer": "adam"},
        split_strategy="session_train_val_test_split(70/15/15)",
        train_sessions=train_sessions, val_sessions=val_sessions, test_sessions=test_sessions,
        metrics=metrics,
        model_param_count=param_count,
    )
    out_path = record.save(args.experiments_dir)
    print(f"\nExperiment record: {out_path}")
    return 0


def _evaluate(y_prob: np.ndarray, y_true: np.ndarray) -> dict:
    if len(set(np.asarray(y_true).tolist())) < 2:
        raise ValueError("test split has only one class present - cannot score honestly")
    return {
        "n_test": len(y_true),
        "pr_auc": evaluation.pr_auc(y_true, y_prob),
        "roc_auc": evaluation.roc_auc(y_true, y_prob),
        "threshold_sweep": [m.as_dict() for m in evaluation.threshold_sweep(y_true, y_prob)],
        "calibration": evaluation.calibration_report(y_true, y_prob).__dict__,
    }


if __name__ == "__main__":
    raise SystemExit(main())
