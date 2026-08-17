"""
Phase 10/11: session-based and chronological splitting.

Individual rows/sequences must NEVER be randomly split across train/val/test - adjacent
observations from the same network incident are highly correlated, so a random row-level
split would leak the answer. Every function here assigns whole SESSIONS to exactly one split.
"""

from __future__ import annotations

import hashlib
import random
from dataclasses import dataclass, field


@dataclass
class SessionSplit:
    train_sessions: list[str] = field(default_factory=list)
    val_sessions: list[str] = field(default_factory=list)
    test_sessions: list[str] = field(default_factory=list)

    def split_of(self, session_id: str) -> str:
        if session_id in self.train_sessions:
            return "train"
        if session_id in self.val_sessions:
            return "val"
        if session_id in self.test_sessions:
            return "test"
        raise KeyError(f"session {session_id} was not assigned to any split")


def session_train_val_test_split(
    session_ids: list[str],
    train_frac: float = 0.70,
    val_frac: float = 0.15,
    test_frac: float = 0.15,
    seed: int = 42,
) -> SessionSplit:
    """Randomly (but reproducibly, via `seed`) assigns whole sessions to train/val/test.

    Raises ValueError rather than silently producing a degenerate split if there are too
    few distinct sessions to honor the requested fractions - callers should fall back to
    `grouped_cross_validation_folds()` in that case (see MIN_SESSIONS_FOR_HOLDOUT_SPLIT).
    """
    if abs((train_frac + val_frac + test_frac) - 1.0) > 1e-6:
        raise ValueError("train/val/test fractions must sum to 1.0")

    unique_sessions = sorted(set(session_ids))
    n = len(unique_sessions)
    if n < MIN_SESSIONS_FOR_HOLDOUT_SPLIT:
        raise ValueError(
            f"Only {n} distinct sessions available; a held-out train/val/test split needs "
            f"at least {MIN_SESSIONS_FOR_HOLDOUT_SPLIT} to give each split a meaningful, "
            f"non-trivial sample. Use grouped_cross_validation_folds() instead."
        )

    rng = random.Random(seed)
    shuffled = unique_sessions[:]
    rng.shuffle(shuffled)

    n_train = max(1, round(n * train_frac))
    n_val = max(1, round(n * val_frac))
    # Whatever's left goes to test, guaranteeing every session is assigned exactly once.
    n_train = min(n_train, n - 2)  # always leave >=1 for val and >=1 for test
    n_val = min(n_val, n - n_train - 1)

    train = shuffled[:n_train]
    val = shuffled[n_train : n_train + n_val]
    test = shuffled[n_train + n_val :]

    return SessionSplit(train_sessions=train, val_sessions=val, test_sessions=test)


MIN_SESSIONS_FOR_HOLDOUT_SPLIT = 10  # below this, prefer grouped CV over a single holdout split


def grouped_cross_validation_folds(session_ids: list[str], n_folds: int = 5, seed: int = 42) -> list[SessionSplit]:
    """Fallback for too few sessions to hold out a meaningful test set: k-fold, grouped by
    session, so every fold still keeps whole sessions together and every session is used
    for validation/test exactly once across the folds."""
    unique_sessions = sorted(set(session_ids))
    n = len(unique_sessions)
    if n < 2:
        raise ValueError(f"Need at least 2 distinct sessions for cross-validation, got {n}.")
    n_folds = min(n_folds, n)

    rng = random.Random(seed)
    shuffled = unique_sessions[:]
    rng.shuffle(shuffled)

    folds: list[list[str]] = [[] for _ in range(n_folds)]
    for i, session in enumerate(shuffled):
        folds[i % n_folds].append(session)

    splits = []
    for i in range(n_folds):
        test = folds[i]
        remaining = [s for j, f in enumerate(folds) if j != i for s in f]
        # A small slice of the remaining (non-test) sessions becomes validation.
        n_val = max(1, len(remaining) // 5) if len(remaining) >= 5 else 0
        val = remaining[:n_val]
        train = remaining[n_val:]
        splits.append(SessionSplit(train_sessions=train, val_sessions=val, test_sessions=test))
    return splits


def chronological_split(
    session_id_to_start_timestamp: dict[str, int],
    train_frac: float = 0.70,
    val_frac: float = 0.15,
) -> SessionSplit:
    """Phase 11: orders sessions by their first observation's timestamp and assigns the
    oldest sessions to train, a middle slice to validation, and the newest to test - so we
    can ask whether a model trained on past behavior still works on later behavior. This is
    a DIFFERENT split from session_train_val_test_split() and is meant to be run as a
    separate, additional evaluation, not a replacement for the randomized session split.
    """
    ordered = sorted(session_id_to_start_timestamp.items(), key=lambda kv: kv[1])
    ordered_sessions = [s for s, _ in ordered]
    n = len(ordered_sessions)
    n_train = max(1, round(n * train_frac))
    n_val = max(1, round(n * val_frac))
    n_train = min(n_train, n - 2)
    n_val = min(n_val, n - n_train - 1)

    train = ordered_sessions[:n_train]
    val = ordered_sessions[n_train : n_train + n_val]
    test = ordered_sessions[n_train + n_val :]
    return SessionSplit(train_sessions=train, val_sessions=val, test_sessions=test)


def stable_session_bucket(session_id: str, num_buckets: int = 100) -> int:
    """Deterministic (across runs/machines) hash bucket for a session id - useful for
    sanity-checking that a split assignment is reproducible without relying on Python's
    randomized string hashing."""
    digest = hashlib.sha256(session_id.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % num_buckets
