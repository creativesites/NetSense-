"""Tests for src/models.py - baseline and candidate model construction."""

from __future__ import annotations

import numpy as np

from src import features, models


def _dummy_windows(n: int, seed: int = 0) -> np.ndarray:
    rng = np.random.default_rng(seed)
    return rng.uniform(0, 1, size=(n, features.WINDOW_SIZE, features.FEATURE_COUNT)).astype(np.float32)


def test_majority_baseline_predicts_training_prevalence():
    baseline = models.MajorityBaseline().fit(np.array([1, 1, 1, 0]))
    probs = baseline.predict_proba(5)
    assert np.allclose(probs, 0.75)


def test_rule_based_baseline_flags_high_packet_loss():
    X = _dummy_windows(4)
    X[0, -1, 4] = 0.9  # packet_loss feature, last timestep, well above the 0.15 threshold
    X[1, -1, 4] = 0.0  # healthy
    rule = models.RuleBasedBaseline()
    risk = rule.predict_proba(X)
    assert risk[0] == 1.0
    assert risk[1] == 0.0
    assert rule.param_count() == 0


def test_rule_based_baseline_ignores_the_missing_sentinel():
    X = _dummy_windows(1)
    X[0, -1, 5] = features.MISSING_VALUE_SENTINEL  # rsrp missing, must not be treated as "extremely weak signal"
    X[0, -1, 4] = 0.0  # no packet loss
    X[0, -1, 10] = 0.0  # no consecutive failures
    X[0, -1, 7] = features.MISSING_VALUE_SENTINEL  # wifi rssi also missing
    risk = models.RuleBasedBaseline().predict_proba(X)
    assert risk[0] == 0.0


def test_flatten_windows_shape():
    X = _dummy_windows(3)
    flat = models.flatten_windows(X)
    assert flat.shape == (3, features.WINDOW_SIZE * features.FEATURE_COUNT)


def test_build_logistic_regression_fits_and_predicts():
    X = models.flatten_windows(_dummy_windows(40))
    y = (np.arange(40) % 2)  # alternating classes so both are present
    clf = models.build_logistic_regression()
    clf.fit(X, y)
    probs = clf.predict_proba(X)[:, 1]
    assert probs.shape == (40,)


def test_build_mlp_output_shape_and_nonzero_params():
    model = models.build_mlp(n_outputs=1)
    out = model.predict(_dummy_windows(2), verbose=0)
    assert out.shape == (2, 1)
    assert models.keras_param_count(model) > 0


def test_build_cnn_default_heads_shape():
    model = models.build_cnn()
    out = model.predict(_dummy_windows(2), verbose=0)
    assert set(out.keys()) == {"dropout30s", "degradation15s"}
    assert out["dropout30s"].shape == (2, 1)
    assert out["degradation15s"].shape == (2, 1)


def test_build_cnn_anomaly_head_is_off_by_default():
    """Documents the known gap: no ground-truth anomaly label exists yet (Phase 8/models.py docstring)."""
    model = models.build_cnn()
    out = model.predict(_dummy_windows(1), verbose=0)
    assert "anomaly_score" not in out


def test_build_single_task_cnn_has_exactly_one_head():
    model = models.build_single_task_cnn("dropout30s")
    out = model.predict(_dummy_windows(2), verbose=0)
    assert set(out.keys()) == {"dropout30s"}


def test_cnn_multi_task_has_fewer_or_equal_params_than_two_single_task_models():
    multi = models.build_cnn(models.CnnHeadsConfig(dropout30s=True, degradation15s=True))
    single_a = models.build_single_task_cnn("dropout30s")
    single_b = models.build_single_task_cnn("degradation15s")
    multi_params = models.keras_param_count(multi)
    combined_single_params = models.keras_param_count(single_a) + models.keras_param_count(single_b)
    # multi-task shares the conv backbone, so it must be meaningfully smaller than two
    # separate single-task models with their own backbones.
    assert multi_params < combined_single_params
