"""
Python mirror of TrainingSequenceBuilder.kt: assembles windowed, labeled training
sequences from a loaded dataset DataFrame (see dataset.py).

Mirrors the Kotlin behavior exactly, including its specific quirks (documented inline)
rather than "fixing" them here - the goal is that an offline-trained model sees the same
feature semantics the Android app will actually produce at inference time.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from . import features
from .leakage_audit import assert_chronological_window


@dataclass
class TrainingSequence:
    session_id: str
    sequence_start: int
    sequence_end: int
    feature_matrix: np.ndarray  # [WINDOW_SIZE, FEATURE_COUNT]
    degradation15s: bool | None
    degradation15s_status: str
    dropout30s: bool | None
    dropout30s_status: str
    likely_cause: str | None
    likely_cause_status: str
    observation_qualities: list[str]
    feature_schema_version: int
    label_schema_version: int | None

    @property
    def is_fully_labeled(self) -> bool:
        return self.degradation15s_status == "RESOLVED" and self.dropout30s_status == "RESOLVED"


def build_sequences(df: pd.DataFrame, window_size: int = features.WINDOW_SIZE) -> list[TrainingSequence]:
    """Mirrors TrainingSequenceBuilder.buildSequences(): excludes INVALID and SYNTHETIC
    rows, never builds a window that straddles two sessionIds, and reuses the SAME
    feature-extraction code the CNN/MLP will train on and the Android app runs at
    inference time (features.extract_window_tensor)."""
    usable = df[(df["observationQuality"] != "INVALID") & (~df["isSynthetic"].fillna(False).astype(bool))]
    usable = usable.sort_values("timestamp")

    sequences: list[TrainingSequence] = []
    for session_id, group in usable.groupby("sessionId", sort=False):
        group = group.sort_values("timestamp").reset_index(drop=True)
        if len(group) < window_size:
            continue

        observations = [features.Observation.from_row(row) for _, row in group.iterrows()]

        for end_idx in range(window_size - 1, len(group)):
            window_rows = group.iloc[end_idx - window_size + 1 : end_idx + 1]
            window_obs = observations[end_idx - window_size + 1 : end_idx + 1]

            assert_chronological_window(window_rows["timestamp"].tolist())

            matrix = features.extract_window_tensor(window_obs)

            defining = window_rows.iloc[-1]
            sequences.append(
                TrainingSequence(
                    session_id=str(session_id),
                    sequence_start=int(window_rows.iloc[0]["timestamp"]),
                    sequence_end=int(defining["timestamp"]),
                    feature_matrix=matrix,
                    degradation15s=_bool_or_none(defining.get("labelDegradation15s")),
                    degradation15s_status=str(defining.get("labelDegradation15sStatus") or "UNRESOLVED"),
                    dropout30s=_bool_or_none(defining.get("labelDropout30s")),
                    dropout30s_status=str(defining.get("labelDropout30sStatus") or "UNRESOLVED"),
                    likely_cause=_str_or_none(defining.get("labelLikelyCause")),
                    likely_cause_status=str(defining.get("labelLikelyCauseStatus") or "UNLABELED"),
                    observation_qualities=window_rows["observationQuality"].astype(str).tolist(),
                    feature_schema_version=int(defining.get("featureSchemaVersion") or features.FEATURE_SCHEMA_VERSION),
                    label_schema_version=_int_or_none(defining.get("labelSchemaVersion")),
                )
            )
    return sequences


def _bool_or_none(value) -> bool | None:
    if value is None or (isinstance(value, float) and np.isnan(value)) or pd.isna(value):
        return None
    return bool(value)


def _str_or_none(value) -> str | None:
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    return str(value)


def _int_or_none(value) -> int | None:
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    return int(value)


def sequences_to_arrays(
    sequences: list[TrainingSequence], task: str, only_resolved: bool = True
) -> tuple[np.ndarray, np.ndarray, list[str]]:
    """Flattens a list of TrainingSequence into (X, y, session_ids) for a given binary task
    ("degradation15s" | "dropout30s"). By default drops sequences whose label for that task
    isn't RESOLVED - training on UNRESOLVED/INSUFFICIENT_DATA rows would mean training
    against a fabricated label, which is exactly what the labeling pipeline was built to
    prevent (see the data-engineering phase's LabelResolver)."""
    if task not in ("degradation15s", "dropout30s"):
        raise ValueError(f"unknown task {task!r}")

    X, y, session_ids = [], [], []
    for seq in sequences:
        value = seq.degradation15s if task == "degradation15s" else seq.dropout30s
        status = seq.degradation15s_status if task == "degradation15s" else seq.dropout30s_status
        if only_resolved and status != "RESOLVED":
            continue
        if value is None:
            continue
        X.append(seq.feature_matrix)
        y.append(1 if value else 0)
        session_ids.append(seq.session_id)

    if not X:
        return np.empty((0, features.WINDOW_SIZE, features.FEATURE_COUNT)), np.empty((0,)), []
    return np.stack(X), np.array(y), session_ids
