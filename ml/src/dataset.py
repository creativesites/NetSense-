"""
Schema mirror + loader/validator for the NetPulse ML telemetry dataset.

This module is the single source of truth, on the Python side, for what a NetPulse
dataset export (CSV or JSONL, produced by DatasetExportService.kt on-device) looks like.
Every column name and type here must match
`app/src/main/java/com/netsense/netpulse/dataset/DatasetExportService.kt` exactly - if the
Kotlin side changes its export schema, this file (and PULSE_EXPORT_SCHEMA_VERSION_NOTE
below) must be updated in the same change.

IMPORTANT: this module does not fabricate or synthesize telemetry. `load_csv`/`load_jsonl`
only ever read a real file the caller points them at. If no such file exists yet, calling
code must say so explicitly rather than inventing a DataFrame.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import pandas as pd

# Mirrors DatasetExportService.CSV_HEADER (Kotlin) exactly, in the same order.
EXPECTED_COLUMNS: list[str] = [
    "sessionId",
    "timestamp",
    "transport",
    "networkIdHash",
    "rsrpDbm",
    "rsrqDb",
    "sinrDb",
    "cqi",
    "wifiRssiDbm",
    "dnsLatencyMs",
    "dnsSuccess",
    "tcpRttMs",
    "tcpSuccess",
    "tcpJitterMs",
    "httpTtfbMs",
    "httpSuccess",
    "isCaptivePortal",
    "httpStatusCode",
    "packetLossPct",
    "consecutiveProbeFailures",
    "usabilityScore",
    "isValidated",
    "isZombie",
    "observationQuality",
    "isSynthetic",
    "labelDegradation15s",
    "labelDegradation15sStatus",
    "labelDropout30s",
    "labelDropout30sStatus",
    "labelLikelyCause",
    "labelLikelyCauseStatus",
    "labelSchemaVersion",
    "featureSchemaVersion",
]

# Columns whose Kotlin type is nullable and therefore must be able to hold NaN/None -
# a missing value here means "not measured", never "measured as zero/false".
NULLABLE_COLUMNS: set[str] = {
    "rsrpDbm",
    "rsrqDb",
    "sinrDb",
    "cqi",
    "wifiRssiDbm",
    "dnsLatencyMs",
    "tcpRttMs",
    "tcpJitterMs",
    "httpTtfbMs",
    "httpStatusCode",
    "labelDegradation15s",
    "labelDropout30s",
    "labelLikelyCause",
}

BOOLEAN_COLUMNS: set[str] = {
    "dnsSuccess",
    "tcpSuccess",
    "httpSuccess",
    "isCaptivePortal",
    "isValidated",
    "isZombie",
    "isSynthetic",
    "labelDegradation15s",  # nullable boolean
    "labelDropout30s",  # nullable boolean
}

# Mirrors com.netsense.netpulse.dataset.ObservationQuality
OBSERVATION_QUALITY_VALUES = {"VALID", "PARTIAL", "INVALID", "SYNTHETIC"}

# Mirrors com.netsense.netpulse.dataset.LabelResolutionStatus
LABEL_STATUS_VALUES = {"UNRESOLVED", "RESOLVED", "INSUFFICIENT_DATA"}

# Mirrors com.netsense.netpulse.dataset.LikelyCauseStatus
CAUSE_STATUS_VALUES = {"UNLABELED", "RESOLVED"}

# Mirrors LabelSemantics.inferLikelyCause's possible outputs (com.netsense.netpulse.dataset.LabelSemantics)
LIKELY_CAUSE_VALUES = {
    "RF_FADING",
    "UPSTREAM_CONGESTION",
    "DNS_BLACKHOLE",
    "CAPTIVE_PORTAL",
    "GATEWAY_DEAD",
}


@dataclass
class SchemaIssue:
    severity: str  # "error" | "warning"
    message: str


def load_csv(path: str | Path) -> pd.DataFrame:
    """Loads a real DatasetExportService CSV export. Raises if the file doesn't exist -
    never silently returns an empty/synthetic frame."""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(
            f"No dataset export found at {path}. This tool never fabricates data - "
            f"export a real dataset from the NetPulse app first (Settings > Analytics > "
            f"'Export ML Training Dataset')."
        )
    df = pd.read_csv(path)
    return _coerce_types(df)


def load_jsonl(path: str | Path) -> pd.DataFrame:
    """Loads a real DatasetExportService JSONL export, flattening the nested `labels` object."""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(
            f"No dataset export found at {path}. This tool never fabricates data - "
            f"export a real dataset from the NetPulse app first (Settings > Analytics > "
            f"'Export ML Training Dataset')."
        )
    rows = []
    with path.open("r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError as e:
                raise ValueError(f"{path}:{line_no}: invalid JSON line: {e}") from e
            labels = obj.pop("labels", {})
            obj["labelDegradation15s"] = labels.get("degradation15s")
            obj["labelDegradation15sStatus"] = labels.get("degradation15sStatus")
            obj["labelDropout30s"] = labels.get("dropout30s")
            obj["labelDropout30sStatus"] = labels.get("dropout30sStatus")
            obj["labelLikelyCause"] = labels.get("likelyCause")
            obj["labelLikelyCauseStatus"] = labels.get("likelyCauseStatus")
            obj["labelSchemaVersion"] = labels.get("schemaVersion")
            rows.append(obj)
    if not rows:
        raise ValueError(f"{path} contains no observations (0 JSONL lines).")
    df = pd.DataFrame(rows)
    return _coerce_types(df)


def _coerce_types(df: pd.DataFrame) -> pd.DataFrame:
    for col in BOOLEAN_COLUMNS:
        if col in df.columns:
            df[col] = df[col].astype("boolean")  # pandas nullable boolean (supports NA)
    numeric_cols = [
        "timestamp", "rsrpDbm", "rsrqDb", "sinrDb", "cqi", "wifiRssiDbm",
        "dnsLatencyMs", "tcpRttMs", "tcpJitterMs", "httpTtfbMs", "httpStatusCode",
        "packetLossPct", "consecutiveProbeFailures", "usabilityScore",
        "labelSchemaVersion", "featureSchemaVersion",
    ]
    for col in numeric_cols:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")
    return df


def validate_schema(df: pd.DataFrame) -> list[SchemaIssue]:
    """Structural validation only (Phase 1/2) - does not judge data quality/content, see
    scripts/inspect_dataset.py and scripts/validate_dataset.py for that."""
    issues: list[SchemaIssue] = []

    missing = [c for c in EXPECTED_COLUMNS if c not in df.columns]
    if missing:
        issues.append(SchemaIssue("error", f"Missing expected columns: {missing}"))

    extra = [c for c in df.columns if c not in EXPECTED_COLUMNS]
    if extra:
        issues.append(SchemaIssue("warning", f"Unexpected extra columns: {extra}"))

    if "observationQuality" in df.columns:
        bad = set(df["observationQuality"].dropna().unique()) - OBSERVATION_QUALITY_VALUES
        if bad:
            issues.append(SchemaIssue("error", f"Unknown observationQuality values: {bad}"))

    for status_col, allowed in (
        ("labelDegradation15sStatus", LABEL_STATUS_VALUES),
        ("labelDropout30sStatus", LABEL_STATUS_VALUES),
        ("labelLikelyCauseStatus", CAUSE_STATUS_VALUES),
    ):
        if status_col in df.columns:
            bad = set(df[status_col].dropna().unique()) - allowed
            if bad:
                issues.append(SchemaIssue("error", f"Unknown {status_col} values: {bad}"))

    if "labelLikelyCause" in df.columns:
        bad = set(df["labelLikelyCause"].dropna().unique()) - LIKELY_CAUSE_VALUES
        if bad:
            issues.append(SchemaIssue("error", f"Unknown labelLikelyCause values: {bad}"))

    # A label's boolean value must never be present without a RESOLVED status, and must
    # never be absent while RESOLVED - this is the core "don't fabricate false" guarantee
    # from the labeling pipeline; if it's ever violated the export (or the resolver) has a bug.
    for value_col, status_col in (
        ("labelDegradation15s", "labelDegradation15sStatus"),
        ("labelDropout30s", "labelDropout30sStatus"),
    ):
        if value_col in df.columns and status_col in df.columns:
            resolved_but_missing = df[(df[status_col] == "RESOLVED") & (df[value_col].isna())]
            if len(resolved_but_missing) > 0:
                issues.append(
                    SchemaIssue(
                        "error",
                        f"{len(resolved_but_missing)} rows have {status_col}=RESOLVED but "
                        f"{value_col} is null - a resolved label must always carry a value.",
                    )
                )
            unresolved_but_present = df[(df[status_col] != "RESOLVED") & (df[value_col].notna())]
            if len(unresolved_but_present) > 0:
                issues.append(
                    SchemaIssue(
                        "error",
                        f"{len(unresolved_but_present)} rows have a non-RESOLVED {status_col} "
                        f"but a non-null {value_col} - a label must never carry a value while "
                        f"not RESOLVED (this is exactly the 'false just because the future "
                        f"hasn't happened yet' bug the labeling pipeline exists to prevent).",
                    )
                )

    if "isSynthetic" in df.columns:
        synthetic_count = int(df["isSynthetic"].fillna(False).sum())
        if synthetic_count > 0:
            issues.append(
                SchemaIssue(
                    "warning",
                    f"{synthetic_count} synthetic (FaultSimulator) rows present in this "
                    f"export - DatasetExportService should exclude these by default; if this "
                    f"file came from `getProductionObservations()` this indicates a bug.",
                )
            )

    if "featureSchemaVersion" in df.columns:
        versions = set(df["featureSchemaVersion"].dropna().unique())
        if len(versions) > 1:
            issues.append(
                SchemaIssue(
                    "error",
                    f"Multiple featureSchemaVersion values present in one export: {versions}. "
                    f"Rows produced under different feature schemas must not be mixed into one "
                    f"training run without explicit version-aware handling.",
                )
            )

    if "labelSchemaVersion" in df.columns:
        versions = set(df["labelSchemaVersion"].dropna().unique()) - {0}
        if len(versions) > 1:
            issues.append(
                SchemaIssue(
                    "error",
                    f"Multiple labelSchemaVersion values present in one export: {versions}.",
                )
            )

    return issues
