"""
Phase 21: reproducible experiment records.

Every training/evaluation run must produce one of these before its metrics are considered
reportable. Saved as JSON under ml/experiments/<timestamp>_<name>.json.
"""

from __future__ import annotations

import json
import platform
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from . import features


@dataclass
class ExperimentRecord:
    name: str
    seed: int
    dataset_source_path: str
    dataset_row_count: int
    feature_schema_version: int = features.FEATURE_SCHEMA_VERSION
    label_schema_version: int | None = None  # filled from the actual dataset, not assumed
    model_architecture: str = ""
    hyperparameters: dict = field(default_factory=dict)
    split_strategy: str = ""
    train_sessions: int = 0
    val_sessions: int = 0
    test_sessions: int = 0
    metrics: dict = field(default_factory=dict)
    model_param_count: int | None = None
    model_size_bytes: dict = field(default_factory=dict)  # {"fp32": ..., "fp16": ..., "int8": ...}
    python_version: str = field(default_factory=platform.python_version)
    created_at_utc: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    notes: str = ""

    def save(self, experiments_dir: str | Path) -> Path:
        experiments_dir = Path(experiments_dir)
        experiments_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        safe_name = "".join(c if c.isalnum() or c in "-_" else "_" for c in self.name)
        out_path = experiments_dir / f"{timestamp}_{safe_name}.json"
        out_path.write_text(json.dumps(asdict(self), indent=2, default=str))
        return out_path
