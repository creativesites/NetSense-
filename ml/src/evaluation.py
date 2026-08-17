"""
Phase 9/15/16/17: evaluation beyond accuracy, threshold calibration, probability
calibration, and feature importance.

Nothing in this module invents numbers - every function here operates on arrays the
caller supplies (real predictions against real labels). Scripts that don't yet have real
data must not call these functions and then report the output as if it were a real result.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from . import features


@dataclass
class BinaryMetrics:
    threshold: float
    n: int
    n_positive: int
    n_negative: int
    tp: int
    fp: int
    tn: int
    fn: int
    precision: float
    recall: float
    f1: float
    specificity: float
    false_positive_rate: float
    false_negative_rate: float

    def as_dict(self) -> dict:
        return self.__dict__.copy()


def compute_binary_metrics(y_true: np.ndarray, y_prob: np.ndarray, threshold: float) -> BinaryMetrics:
    y_true = np.asarray(y_true).astype(int)
    y_pred = (np.asarray(y_prob) >= threshold).astype(int)

    tp = int(np.sum((y_pred == 1) & (y_true == 1)))
    fp = int(np.sum((y_pred == 1) & (y_true == 0)))
    tn = int(np.sum((y_pred == 0) & (y_true == 0)))
    fn = int(np.sum((y_pred == 0) & (y_true == 1)))

    precision = tp / (tp + fp) if (tp + fp) > 0 else 0.0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0.0
    specificity = tn / (tn + fp) if (tn + fp) > 0 else 0.0
    fpr = fp / (fp + tn) if (fp + tn) > 0 else 0.0
    fnr = fn / (fn + tp) if (fn + tp) > 0 else 0.0

    return BinaryMetrics(
        threshold=threshold,
        n=len(y_true),
        n_positive=int(np.sum(y_true == 1)),
        n_negative=int(np.sum(y_true == 0)),
        tp=tp, fp=fp, tn=tn, fn=fn,
        precision=precision, recall=recall, f1=f1, specificity=specificity,
        false_positive_rate=fpr, false_negative_rate=fnr,
    )


def pr_auc(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    from sklearn.metrics import average_precision_score

    y_true = np.asarray(y_true)
    if len(set(y_true.tolist())) < 2:
        raise ValueError("PR-AUC is undefined with only one class present in y_true")
    return float(average_precision_score(y_true, y_prob))


def roc_auc(y_true: np.ndarray, y_prob: np.ndarray) -> float:
    from sklearn.metrics import roc_auc_score

    y_true = np.asarray(y_true)
    if len(set(y_true.tolist())) < 2:
        raise ValueError("ROC-AUC is undefined with only one class present in y_true")
    return float(roc_auc_score(y_true, y_prob))


DEFAULT_THRESHOLDS = [0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90]


def threshold_sweep(y_true: np.ndarray, y_prob: np.ndarray, thresholds: list[float] = DEFAULT_THRESHOLDS) -> list[BinaryMetrics]:
    """Phase 15: never assume 0.5 is the right operating point - report the full tradeoff."""
    return [compute_binary_metrics(y_true, y_prob, t) for t in thresholds]


@dataclass
class CalibrationReport:
    n_bins: int
    bin_confidence: list[float]  # mean predicted probability in each bin
    bin_accuracy: list[float]  # actual positive rate in each bin
    bin_count: list[int]
    brier_score: float
    expected_calibration_error: float


def calibration_report(y_true: np.ndarray, y_prob: np.ndarray, n_bins: int = 10) -> CalibrationReport:
    """Phase 16: is a stated '90% dropout probability' actually right ~90% of the time?
    Uses equal-width probability bins (the standard reliability-diagram construction)."""
    y_true = np.asarray(y_true).astype(float)
    y_prob = np.asarray(y_prob).astype(float)

    bin_edges = np.linspace(0.0, 1.0, n_bins + 1)
    bin_idx = np.clip(np.digitize(y_prob, bin_edges[1:-1]), 0, n_bins - 1)

    bin_confidence, bin_accuracy, bin_count = [], [], []
    ece = 0.0
    n = len(y_true)
    for b in range(n_bins):
        mask = bin_idx == b
        count = int(np.sum(mask))
        bin_count.append(count)
        if count == 0:
            bin_confidence.append(float("nan"))
            bin_accuracy.append(float("nan"))
            continue
        conf = float(np.mean(y_prob[mask]))
        acc = float(np.mean(y_true[mask]))
        bin_confidence.append(conf)
        bin_accuracy.append(acc)
        ece += (count / n) * abs(acc - conf)

    brier = float(np.mean((y_prob - y_true) ** 2))

    return CalibrationReport(
        n_bins=n_bins,
        bin_confidence=bin_confidence,
        bin_accuracy=bin_accuracy,
        bin_count=bin_count,
        brier_score=brier,
        expected_calibration_error=ece,
    )


def logistic_regression_importance(model, feature_names: list[str] | None = None) -> list[tuple[str, float]]:
    """Phase 17: for a linear model, |coefficient| is a direct, honest importance signal."""
    coefs = np.asarray(model.coef_).reshape(-1)
    names = feature_names or [f"f{i}" for i in range(len(coefs))]
    ranked = sorted(zip(names, np.abs(coefs).tolist()), key=lambda kv: kv[1], reverse=True)
    return ranked


def permutation_importance(
    predict_fn,
    X: np.ndarray,
    y_true: np.ndarray,
    metric_fn=pr_auc,
    n_repeats: int = 5,
    seed: int = 42,
) -> list[tuple[str, float]]:
    """Phase 17, generic to any model type (CNN/MLP included): shuffle one feature column
    across the WHOLE window at a time and measure the metric drop. `predict_fn` must accept
    an [n, WINDOW_SIZE, FEATURE_COUNT] array and return probabilities."""
    rng = np.random.default_rng(seed)
    baseline_prob = predict_fn(X)
    baseline_score = metric_fn(y_true, baseline_prob)

    importances = []
    for feature_idx, name in enumerate(features.FEATURE_NAMES):
        drops = []
        for _ in range(n_repeats):
            X_perm = X.copy()
            perm = rng.permutation(X_perm.shape[0])
            X_perm[:, :, feature_idx] = X_perm[perm, :, feature_idx]
            perm_prob = predict_fn(X_perm)
            perm_score = metric_fn(y_true, perm_prob)
            drops.append(baseline_score - perm_score)
        importances.append((name, float(np.mean(drops))))

    return sorted(importances, key=lambda kv: kv[1], reverse=True)
