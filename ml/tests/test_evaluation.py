"""Tests for src/evaluation.py - metrics, threshold sweep, calibration, importance."""

from __future__ import annotations

import numpy as np
import pytest

from src import evaluation, features


def test_compute_binary_metrics_known_confusion_matrix():
    # y_pred @ 0.5 = [1,1,1,0,0,0]. Actual positives at idx 0,1,5; actual negatives at idx 2,3,4.
    # -> TP=2 (idx0,1), FN=1 (idx5), FP=1 (idx2), TN=2 (idx3,4).
    y_true = np.array([1, 1, 0, 0, 0, 1])
    y_prob = np.array([0.9, 0.6, 0.7, 0.1, 0.2, 0.3])  # last one (true positive) predicted negative
    m = evaluation.compute_binary_metrics(y_true, y_prob, threshold=0.5)

    assert m.tp == 2
    assert m.fp == 1
    assert m.tn == 2
    assert m.fn == 1
    assert m.precision == pytest.approx(2 / 3)
    assert m.recall == pytest.approx(2 / 3)
    assert m.false_positive_rate == pytest.approx(1 / 3)
    assert m.false_negative_rate == pytest.approx(1 / 3)


def test_compute_binary_metrics_handles_degenerate_all_negative_predictions():
    y_true = np.array([1, 0, 0])
    y_prob = np.array([0.1, 0.1, 0.1])
    m = evaluation.compute_binary_metrics(y_true, y_prob, threshold=0.5)
    assert m.tp == 0 and m.fp == 0
    assert m.precision == 0.0  # 0/0 guarded, not NaN/crash
    assert m.recall == 0.0


def test_pr_auc_and_roc_auc_require_both_classes():
    with pytest.raises(ValueError):
        evaluation.pr_auc(np.array([1, 1, 1]), np.array([0.9, 0.8, 0.7]))
    with pytest.raises(ValueError):
        evaluation.roc_auc(np.array([0, 0, 0]), np.array([0.1, 0.2, 0.3]))


def test_pr_auc_perfect_separation_is_1():
    y_true = np.array([0, 0, 1, 1])
    y_prob = np.array([0.1, 0.2, 0.8, 0.9])
    assert evaluation.pr_auc(y_true, y_prob) == pytest.approx(1.0)


def test_threshold_sweep_covers_all_default_thresholds():
    y_true = np.array([1, 0, 1, 0])
    y_prob = np.array([0.9, 0.1, 0.6, 0.4])
    rows = evaluation.threshold_sweep(y_true, y_prob)
    assert [r.threshold for r in rows] == evaluation.DEFAULT_THRESHOLDS


def test_calibration_report_perfectly_calibrated_toy_example():
    rng = np.random.default_rng(0)
    y_prob = rng.uniform(0, 1, size=2000)
    y_true = (rng.uniform(0, 1, size=2000) < y_prob).astype(int)  # by construction, well-calibrated

    report = evaluation.calibration_report(y_true, y_prob, n_bins=10)
    assert report.expected_calibration_error < 0.05  # should be small for a well-calibrated source
    assert report.brier_score < 0.3


def test_calibration_report_detects_overconfident_predictions():
    y_true = np.array([0] * 90 + [1] * 10)  # true positive rate = 10%
    y_prob = np.full(100, 0.9)  # but the model always says 90% confident
    report = evaluation.calibration_report(y_true, y_prob, n_bins=10)
    assert report.expected_calibration_error > 0.5  # badly miscalibrated


def test_logistic_regression_importance_ranks_by_magnitude():
    class FakeLinearModel:
        coef_ = np.array([[0.1, -5.0, 0.3]])

    ranked = evaluation.logistic_regression_importance(FakeLinearModel(), feature_names=["a", "b", "c"])
    assert ranked[0][0] == "b"  # largest |coefficient|


def test_permutation_importance_flags_the_informative_feature():
    rng = np.random.default_rng(0)
    n = 300
    X = rng.uniform(0, 1, size=(n, features.WINDOW_SIZE, features.FEATURE_COUNT)).astype(np.float32)
    # y depends ONLY on feature index 4 (packet_loss) at the last timestep.
    y = (X[:, -1, 4] > 0.5).astype(int)

    def predict_fn(X_in):
        return X_in[:, -1, 4]

    ranked = evaluation.permutation_importance(predict_fn, X, y, n_repeats=3, seed=1)
    top_feature = ranked[0][0]
    assert top_feature == "packet_loss"
