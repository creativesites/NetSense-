"""
Tests for scripts/merge_raw_exports.py's deduplication - guards against the real issue
found in practice: DatasetExportService exports the ENTIRE accumulated history every time,
so two exports from the same device overlap heavily and must be deduplicated, not
concatenated.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts import merge_raw_exports
from tests.fixtures import make_observation_row


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    with path.open("w") as f:
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


def test_overlapping_full_history_exports_are_deduplicated_not_doubled(tmp_path):
    raw_dir = tmp_path / "raw_exports"
    raw_dir.mkdir()

    # Export 1 (earlier): 2 rows, dropout30s still UNRESOLVED (not enough future data yet).
    early_rows = [
        make_observation_row(1, "s1", 0, label_dropout30s_status="UNRESOLVED"),
        make_observation_row(2, "s1", 1000, label_dropout30s_status="UNRESOLVED"),
    ]
    _write_jsonl(raw_dir / "20260101_0000_device_001.jsonl", early_rows)

    # Export 2 (later): the SAME 2 rows (full-history re-export) now RESOLVED, plus 1 new row.
    later_rows = [
        make_observation_row(1, "s1", 0, label_dropout30s_status="RESOLVED", label_dropout30s=True),
        make_observation_row(2, "s1", 1000, label_dropout30s_status="RESOLVED", label_dropout30s=True),
        make_observation_row(3, "s1", 2000, label_dropout30s_status="UNRESOLVED"),
    ]
    _write_jsonl(raw_dir / "20260102_0000_device_002.jsonl", later_rows)

    out_path = tmp_path / "merged.jsonl"
    argv_backup = sys.argv
    try:
        sys.argv = ["merge_raw_exports.py", "--raw-dir", str(raw_dir), "--output", str(out_path)]
        code = merge_raw_exports.main()
    finally:
        sys.argv = argv_backup

    assert code == 0
    lines = out_path.read_text().strip().splitlines()
    assert len(lines) == 3  # not 5 - the 2 overlapping rows were deduplicated, not doubled

    parsed = [json.loads(line) for line in lines]
    by_ts = {p["timestamp"]: p for p in parsed}
    # The kept copy of the overlapping rows must be the LATER (better-labeled) version.
    assert by_ts[0]["labels"]["dropout30sStatus"] == "RESOLVED"
    assert by_ts[0]["labels"]["dropout30s"] is True
    assert by_ts[1000]["labels"]["dropout30sStatus"] == "RESOLVED"
    assert 2000 in by_ts


def test_merge_fails_loudly_with_no_raw_files(tmp_path, capsys):
    empty_dir = tmp_path / "empty"
    empty_dir.mkdir()
    argv_backup = sys.argv
    try:
        sys.argv = ["merge_raw_exports.py", "--raw-dir", str(empty_dir), "--output", str(tmp_path / "out.jsonl")]
        code = merge_raw_exports.main()
    finally:
        sys.argv = argv_backup
    assert code == 2
    assert "NO DATA" in capsys.readouterr().err
