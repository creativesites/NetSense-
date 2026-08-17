"""Tests for src/splitting.py."""

from __future__ import annotations

import pytest

from src import splitting


def test_session_split_assigns_every_session_exactly_once():
    sessions = [f"s{i}" for i in range(20)]
    split = splitting.session_train_val_test_split(sessions, seed=1)

    all_assigned = split.train_sessions + split.val_sessions + split.test_sessions
    assert sorted(all_assigned) == sorted(sessions)
    assert len(set(all_assigned)) == len(sessions)  # no duplicates / no double assignment


def test_session_split_is_reproducible_with_the_same_seed():
    sessions = [f"s{i}" for i in range(20)]
    a = splitting.session_train_val_test_split(sessions, seed=7)
    b = splitting.session_train_val_test_split(sessions, seed=7)
    assert a.train_sessions == b.train_sessions
    assert a.val_sessions == b.val_sessions
    assert a.test_sessions == b.test_sessions


def test_session_split_raises_with_too_few_sessions():
    with pytest.raises(ValueError, match="grouped_cross_validation_folds"):
        splitting.session_train_val_test_split([f"s{i}" for i in range(3)])


def test_grouped_cv_covers_every_session_as_test_exactly_once():
    sessions = [f"s{i}" for i in range(12)]
    folds = splitting.grouped_cross_validation_folds(sessions, n_folds=4, seed=3)
    test_sessions_seen = [s for fold in folds for s in fold.test_sessions]
    assert sorted(test_sessions_seen) == sorted(sessions)


def test_chronological_split_orders_oldest_to_newest():
    timestamps = {f"s{i}": i * 1000 for i in range(20)}
    split = splitting.chronological_split(timestamps)
    # every train-session timestamp must be <= every test-session timestamp
    max_train_ts = max(timestamps[s] for s in split.train_sessions)
    min_test_ts = min(timestamps[s] for s in split.test_sessions)
    assert max_train_ts <= min_test_ts


def test_stable_session_bucket_is_deterministic():
    assert splitting.stable_session_bucket("abc") == splitting.stable_session_bucket("abc")
