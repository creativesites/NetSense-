"""
Phase 6/7/8: baseline and candidate models for PulsePredictor.

MODEL 0: majority-class baseline.
MODEL 1: rule-based baseline, thresholds taken directly from existing PulseCore/RadarEngine
         semantics (see the comments below citing the exact Kotlin constants), not invented.
MODEL 2: logistic regression (scikit-learn).
MODEL 3: small MLP (tf.keras) - small enough to be a fair "simple" comparison point.
MODEL 4: 1D CNN (tf.keras) - the Phase 7 candidate architecture.

KNOWN GAP (discovered while building this, documented rather than worked around): the
Phase-7 spec's CNN has an "anomaly score" head, but the current label schema
(LABEL_SCHEMA_VERSION = 1, see LabelSemantics.kt) defines only degradation15s, dropout30s,
and likelyCause. `labelAnomaly` exists as a database column but is explicitly documented as
an unpopulated placeholder ("D. PLACEHOLDER... not populated by this phase's LabelResolver"
- TelemetryObservationEntity.kt). There is no ground truth to train an anomaly head against
yet. build_cnn() still exposes the anomaly output (for architectural fidelity to the spec
and so a future anomaly label can be wired in later), but NO training/evaluation code in
this project trains or scores it - see scripts/train_cnn.py.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

import numpy as np

from . import features

TaskName = Literal["degradation15s", "dropout30s", "likely_cause"]

# --- MODEL 0: majority-class baseline ------------------------------------------------


class MajorityBaseline:
    """Predicts the training-set positive prevalence for every example - the minimum bar
    any learned model must clear to be worth its complexity."""

    def __init__(self) -> None:
        self.positive_rate_: float | None = None

    def fit(self, y: np.ndarray) -> "MajorityBaseline":
        y = np.asarray(y, dtype=float)
        self.positive_rate_ = float(np.mean(y)) if len(y) else 0.0
        return self

    def predict_proba(self, n: int) -> np.ndarray:
        if self.positive_rate_ is None:
            raise RuntimeError("call fit() first")
        return np.full(n, self.positive_rate_, dtype=float)

    def param_count(self) -> int:
        return 1


# --- MODEL 1: rule-based baseline -----------------------------------------------------

# Thresholds below are the SAME constants already used in the Kotlin production code, not
# invented for this baseline:
#   RadarEngine.computeStabilityScore(): rsrp < -110 dBm, wifi rssi < -82 dBm, jitter > 150ms
#   LabelSemantics.DEGRADATION_SCORE_THRESHOLD / UsabilityEngine packet-loss bands: loss > 0.15
_RF_FADING_RSRP_NORM = features._normalize(-110.0, features.RSRP_MIN_DBM, features.RSRP_MAX_DBM)
_RF_FADING_WIFI_NORM = features._normalize(-82.0, features.WIFI_RSSI_MIN_DBM, features.WIFI_RSSI_MAX_DBM)
_CONGESTION_JITTER_NORM = features._normalize(150.0, features.TCP_JITTER_MIN_MS, features.TCP_JITTER_MAX_MS)
_HIGH_PACKET_LOSS = 0.15
_HIGH_CONSECUTIVE_FAILURES_NORM = 2.0 / features.MAX_CONSECUTIVE_FAILURES  # >=2 consecutive failures


class RuleBasedBaseline:
    """No learned parameters at all. Looks only at the LAST timestep of the window (i.e.
    "current" state) and flags risk using the same deterministic thresholds PulseCore
    already applies live on-device."""

    def param_count(self) -> int:
        return 0

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        """X: [n, WINDOW_SIZE, FEATURE_COUNT]. Returns a 0/1-ish risk score per example
        (not a calibrated probability - see evaluation.calibration for that distinction)."""
        last = X[:, -1, :]  # most recent timestep
        packet_loss = last[:, 4]
        rsrp = last[:, 5]
        wifi_rssi = last[:, 7]
        consecutive_failures = last[:, 10]

        measured = lambda col: col > (features.MISSING_VALUE_SENTINEL + 1e-6)  # exclude the missing sentinel

        risk = np.zeros(len(last), dtype=float)
        risk = np.maximum(risk, np.where(packet_loss > _HIGH_PACKET_LOSS, 1.0, 0.0))
        risk = np.maximum(
            risk,
            np.where(measured(rsrp) & (rsrp < _RF_FADING_RSRP_NORM), 1.0, 0.0),
        )
        risk = np.maximum(
            risk,
            np.where(measured(wifi_rssi) & (wifi_rssi < _RF_FADING_WIFI_NORM), 1.0, 0.0),
        )
        risk = np.maximum(
            risk,
            np.where(consecutive_failures >= _HIGH_CONSECUTIVE_FAILURES_NORM, 1.0, 0.0),
        )
        return risk


# --- Shared window-flattening for the classical models ---------------------------------


def flatten_windows(X: np.ndarray) -> np.ndarray:
    """[n, WINDOW_SIZE, FEATURE_COUNT] -> [n, WINDOW_SIZE * FEATURE_COUNT] for
    logistic regression / classical sklearn models."""
    n = X.shape[0]
    return X.reshape(n, -1)


# --- MODEL 2: logistic regression -------------------------------------------------------


def build_logistic_regression():
    from sklearn.linear_model import LogisticRegression

    return LogisticRegression(max_iter=1000, class_weight="balanced")


# --- MODEL 3: small MLP (tf.keras, so it can go through the same size/quantization
#     pipeline as the CNN for a fair comparison in Phase 12/13) ---------------------------


def build_mlp(n_outputs: int = 1, output_activation: str = "sigmoid"):
    import tensorflow as tf

    inputs = tf.keras.Input(shape=(features.WINDOW_SIZE, features.FEATURE_COUNT), name="telemetry_window")
    x = tf.keras.layers.Flatten()(inputs)
    x = tf.keras.layers.Dense(16, activation="relu")(x)
    x = tf.keras.layers.Dense(8, activation="relu")(x)
    outputs = tf.keras.layers.Dense(n_outputs, activation=output_activation, name="output")(x)
    return tf.keras.Model(inputs=inputs, outputs=outputs, name="pulse_mlp_baseline")


# --- MODEL 4: 1D CNN (Phase 7 architecture) ---------------------------------------------

N_LIKELY_CAUSE_CLASSES = 5  # RF_FADING, UPSTREAM_CONGESTION, DNS_BLACKHOLE, CAPTIVE_PORTAL, GATEWAY_DEAD


@dataclass
class CnnHeadsConfig:
    """Which of the 4 spec'd heads to actually attach + train (Phase 8: multi-task vs
    single-task). anomaly defaults to False because there is currently no ground-truth
    label to train it against - see the module docstring."""

    dropout30s: bool = True
    degradation15s: bool = True
    likely_cause: bool = False
    anomaly: bool = False


def build_cnn(heads: CnnHeadsConfig = CnnHeadsConfig()):
    """Builds the Phase 7 architecture exactly as specified:
    Conv1D(32,k=3,same,relu) -> BatchNorm -> MaxPool -> Conv1D(48,k=3,same,relu)
    -> GlobalAveragePooling1D -> Dense(32, relu) -> task heads.
    """
    import tensorflow as tf

    inputs = tf.keras.Input(shape=(features.WINDOW_SIZE, features.FEATURE_COUNT), name="telemetry_window")

    x = tf.keras.layers.Conv1D(32, kernel_size=3, padding="same", activation="relu", name="conv1")(inputs)
    x = tf.keras.layers.BatchNormalization(name="bn1")(x)
    x = tf.keras.layers.MaxPooling1D(pool_size=2, padding="same", name="pool1")(x)
    x = tf.keras.layers.Conv1D(48, kernel_size=3, padding="same", activation="relu", name="conv2")(x)
    x = tf.keras.layers.GlobalAveragePooling1D(name="gap")(x)
    x = tf.keras.layers.Dense(32, activation="relu", name="dense")(x)

    outputs = {}
    if heads.anomaly:
        outputs["anomaly_score"] = tf.keras.layers.Dense(1, activation="sigmoid", name="anomaly_score")(x)
    if heads.dropout30s:
        outputs["dropout30s"] = tf.keras.layers.Dense(1, activation="sigmoid", name="dropout30s")(x)
    if heads.degradation15s:
        outputs["degradation15s"] = tf.keras.layers.Dense(1, activation="sigmoid", name="degradation15s")(x)
    if heads.likely_cause:
        outputs["likely_cause"] = tf.keras.layers.Dense(
            N_LIKELY_CAUSE_CLASSES, activation="softmax", name="likely_cause"
        )(x)

    if not outputs:
        raise ValueError("at least one head must be enabled")

    return tf.keras.Model(inputs=inputs, outputs=outputs, name="pulse_predictor_cnn_candidate")


def build_single_task_cnn(task: Literal["dropout30s", "degradation15s"]):
    """Phase 8 variant A/B: a single-output CNN sharing the same conv backbone as build_cnn()."""
    heads = CnnHeadsConfig(dropout30s=(task == "dropout30s"), degradation15s=(task == "degradation15s"))
    return build_cnn(heads)


def keras_param_count(model) -> int:
    return int(sum(np.prod(v.shape) for v in model.trainable_variables))
