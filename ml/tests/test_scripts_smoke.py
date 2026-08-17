"""
End-to-end SMOKE tests for the scripts/ CLI tools, using tiny synthetic fixtures written to
tmp_path. These prove the pipeline code runs without crashing and enforces its "no data ->
say so, exit non-zero" contract. They do NOT constitute, and must never be cited as, a real
dataset inspection or model result.
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts import build_sequences, inspect_dataset, validate_dataset
from src import features
from tests.fixtures import make_session_window


def _write_fixture_csv(tmp_path: Path, n_rows: int = 20) -> Path:
    df = make_session_window(n_rows=n_rows)
    path = tmp_path / "fixture_export.csv"
    df.to_csv(path, index=False)
    return path


def test_inspect_dataset_exits_cleanly_on_missing_file(tmp_path, capsys):
    argv_backup = sys.argv
    try:
        sys.argv = ["inspect_dataset.py", "--input", str(tmp_path / "missing.csv")]
        code = inspect_dataset.main()
    finally:
        sys.argv = argv_backup
    assert code == 2
    captured = capsys.readouterr()
    assert "NO DATA" in captured.err


def test_inspect_dataset_runs_on_a_tiny_fixture(tmp_path, capsys):
    csv_path = _write_fixture_csv(tmp_path, n_rows=features.WINDOW_SIZE + 2)
    report_path = tmp_path / "report.md"
    argv_backup = sys.argv
    try:
        sys.argv = ["inspect_dataset.py", "--input", str(csv_path), "--report", str(report_path)]
        code = inspect_dataset.main()
    finally:
        sys.argv = argv_backup

    assert code == 0
    assert report_path.exists()
    content = report_path.read_text()
    assert "Dataset Size" in content
    assert "Label Distribution" in content


def test_validate_dataset_passes_on_a_tiny_fixture(tmp_path):
    csv_path = _write_fixture_csv(tmp_path, n_rows=features.WINDOW_SIZE)
    argv_backup = sys.argv
    try:
        sys.argv = ["validate_dataset.py", "--input", str(csv_path)]
        code = validate_dataset.main()
    finally:
        sys.argv = argv_backup
    assert code == 0


def test_build_sequences_writes_an_npz(tmp_path):
    csv_path = _write_fixture_csv(
        tmp_path, n_rows=features.WINDOW_SIZE,
    )
    out_path = tmp_path / "sequences.npz"
    argv_backup = sys.argv
    try:
        sys.argv = ["build_sequences.py", "--input", str(csv_path), "--output", str(out_path)]
        code = build_sequences.main()
    finally:
        sys.argv = argv_backup

    assert code == 0
    assert out_path.exists()
    data = np.load(out_path)
    assert data["X_dropout30s"].shape[1:] == (features.WINDOW_SIZE, features.FEATURE_COUNT)
