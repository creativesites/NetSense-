"""Tests for src/dataset.py - schema mirror, loader, validator."""

from __future__ import annotations

import json

import pytest

from src import dataset
from tests.fixtures import coerce, make_session_window


def test_load_csv_raises_clear_error_when_file_missing(tmp_path):
    missing = tmp_path / "does_not_exist.csv"
    with pytest.raises(FileNotFoundError, match="never fabricates data"):
        dataset.load_csv(missing)


def test_load_jsonl_raises_clear_error_when_file_missing(tmp_path):
    missing = tmp_path / "does_not_exist.jsonl"
    with pytest.raises(FileNotFoundError, match="never fabricates data"):
        dataset.load_jsonl(missing)


def test_load_csv_round_trip(tmp_path):
    df = make_session_window(n_rows=3)
    csv_path = tmp_path / "export.csv"
    df.to_csv(csv_path, index=False)

    loaded = dataset.load_csv(csv_path)
    assert len(loaded) == 3
    assert set(dataset.EXPECTED_COLUMNS).issubset(set(loaded.columns))


def test_load_jsonl_round_trip_flattens_labels(tmp_path):
    rows = make_session_window(n_rows=2).to_dict(orient="records")
    jsonl_path = tmp_path / "export.jsonl"
    with jsonl_path.open("w") as f:
        for row in rows:
            obj = dict(row)
            obj["labels"] = {
                "degradation15s": obj.pop("labelDegradation15s"),
                "degradation15sStatus": obj.pop("labelDegradation15sStatus"),
                "dropout30s": obj.pop("labelDropout30s"),
                "dropout30sStatus": obj.pop("labelDropout30sStatus"),
                "likelyCause": obj.pop("labelLikelyCause"),
                "likelyCauseStatus": obj.pop("labelLikelyCauseStatus"),
                "schemaVersion": obj.pop("labelSchemaVersion"),
            }
            f.write(json.dumps(obj) + "\n")

    loaded = dataset.load_jsonl(jsonl_path)
    assert len(loaded) == 2
    assert "labelDegradation15sStatus" in loaded.columns
    assert loaded["labelDegradation15sStatus"].iloc[0] == "UNRESOLVED"


def test_validate_schema_flags_resolved_label_with_missing_value():
    df = coerce(make_session_window(
        n_rows=1, label_dropout30s_status="RESOLVED", label_dropout30s=None,
    ))
    issues = dataset.validate_schema(df)
    messages = [i.message for i in issues if i.severity == "error"]
    assert any("RESOLVED but" in m for m in messages)


def test_validate_schema_flags_unresolved_label_with_present_value():
    df = coerce(make_session_window(
        n_rows=1, label_dropout30s_status="UNRESOLVED", label_dropout30s=True,
    ))
    issues = dataset.validate_schema(df)
    messages = [i.message for i in issues if i.severity == "error"]
    assert any("non-RESOLVED" in m for m in messages)


def test_validate_schema_passes_on_a_well_formed_frame():
    df = coerce(make_session_window(n_rows=15))
    issues = dataset.validate_schema(df)
    errors = [i for i in issues if i.severity == "error"]
    assert errors == []


def test_validate_schema_warns_on_synthetic_rows_in_a_production_export():
    df = coerce(make_session_window(n_rows=1, is_synthetic=True))
    issues = dataset.validate_schema(df)
    assert any("synthetic" in i.message.lower() for i in issues)
