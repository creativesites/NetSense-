"""Tests for src/sequences.py - the Python mirror of TrainingSequenceBuilder.kt."""

from __future__ import annotations

import pandas as pd

from src import features, sequences
from tests.fixtures import coerce, make_observation_row, make_session_window


def test_full_valid_window_produces_exactly_one_sequence():
    df = coerce(make_session_window(n_rows=features.WINDOW_SIZE))
    seqs = sequences.build_sequences(df)
    assert len(seqs) == 1
    assert seqs[0].feature_matrix.shape == (features.WINDOW_SIZE, features.FEATURE_COUNT)


def test_synthetic_rows_are_excluded():
    df = coerce(make_session_window(n_rows=features.WINDOW_SIZE, is_synthetic=True))
    seqs = sequences.build_sequences(df)
    assert seqs == []


def test_invalid_rows_are_excluded():
    rows = [make_observation_row(i, "s1", i * 1000) for i in range(features.WINDOW_SIZE - 1)]
    rows.append(make_observation_row(features.WINDOW_SIZE - 1, "s1", (features.WINDOW_SIZE - 1) * 1000, quality="INVALID"))
    df = coerce(pd.DataFrame(rows))
    seqs = sequences.build_sequences(df)
    assert seqs == []  # only 14 usable rows remain - not enough for one window


def test_a_window_never_spans_two_sessions():
    rows_a = [make_observation_row(i, "session-A", i * 1000) for i in range(8)]
    rows_b = [make_observation_row(100 + i, "session-B", 8000 + i * 1000) for i in range(7)]
    df = coerce(pd.DataFrame(rows_a + rows_b))
    seqs = sequences.build_sequences(df)
    assert seqs == []


def test_sequences_to_arrays_only_includes_resolved_labels_by_default():
    df = coerce(make_session_window(
        n_rows=features.WINDOW_SIZE,
        label_dropout30s_status="UNRESOLVED", label_dropout30s=None,
    ))
    seqs = sequences.build_sequences(df)
    X, y, sessions = sequences.sequences_to_arrays(seqs, "dropout30s", only_resolved=True)
    assert len(y) == 0  # the only sequence's label is UNRESOLVED, so it must be dropped


def test_sequences_to_arrays_includes_a_resolved_positive_label():
    df = coerce(make_session_window(
        n_rows=features.WINDOW_SIZE,
        label_dropout30s_status="RESOLVED", label_dropout30s=True,
    ))
    seqs = sequences.build_sequences(df)
    X, y, sessions = sequences.sequences_to_arrays(seqs, "dropout30s")
    assert len(y) == 1
    assert y[0] == 1
    assert sessions[0] == "test-session-1"
