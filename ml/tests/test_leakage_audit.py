"""Tests for src/leakage_audit.py."""

from __future__ import annotations

import pytest

from src import leakage_audit


def test_current_feature_schema_has_no_leakage_problems():
    problems = leakage_audit.audit_feature_schema()
    assert problems == []


def test_chronological_window_accepts_ascending_timestamps():
    leakage_audit.assert_chronological_window([0, 1000, 2000, 3000])


def test_chronological_window_rejects_out_of_order_timestamps():
    with pytest.raises(AssertionError, match="not chronologically sorted"):
        leakage_audit.assert_chronological_window([0, 2000, 1000])


def test_report_renders_without_error():
    report = leakage_audit.render_leakage_report()
    assert "PASSED" in report
    assert "UNSAFE_AS_FEATURE" in report  # the label columns must be documented as unsafe-as-feature
