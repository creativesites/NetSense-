"""
TEST-ONLY synthetic fixtures.

These exist purely to prove the ml/ tooling code runs correctly (shapes, no crashes,
correct arithmetic on known inputs). They are NEVER real NetPulse telemetry and must NEVER
be used to produce or report a "dataset statistic" or "model performance" result - see
DATA_COLLECTION_STATUS.md for why no such report exists yet.
"""

from __future__ import annotations

import pandas as pd

from src import dataset


def make_observation_row(
    row_id: int,
    session_id: str,
    timestamp_ms: int,
    *,
    transport: str = "WIFI",
    dns_ms: float | None = 40.0,
    tcp_ms: float | None = 60.0,
    jitter_ms: float | None = 10.0,
    http_ms: float | None = 120.0,
    packet_loss: float = 0.0,
    rsrp: float | None = None,
    sinr: float | None = None,
    wifi_rssi: float | None = -55.0,
    consecutive_failures: int = 0,
    usability_score: int = 90,
    is_validated: bool = True,
    is_zombie: bool = False,
    quality: str = "VALID",
    is_synthetic: bool = False,
    label_degradation15s=None,
    label_degradation15s_status: str = "UNRESOLVED",
    label_dropout30s=None,
    label_dropout30s_status: str = "UNRESOLVED",
    label_likely_cause=None,
    label_likely_cause_status: str = "UNLABELED",
    label_schema_version: int = 1,
    feature_schema_version: int = 2,
) -> dict:
    return {
        "sessionId": session_id,
        "timestamp": timestamp_ms,
        "transport": transport,
        "networkIdHash": "deadbeef0000",
        "rsrpDbm": rsrp,
        "rsrqDb": None,
        "sinrDb": sinr,
        "cqi": None,
        "wifiRssiDbm": wifi_rssi,
        "dnsLatencyMs": dns_ms,
        "dnsSuccess": dns_ms is not None,
        "tcpRttMs": tcp_ms,
        "tcpSuccess": tcp_ms is not None,
        "tcpJitterMs": jitter_ms,
        "httpTtfbMs": http_ms,
        "httpSuccess": http_ms is not None,
        "isCaptivePortal": False,
        "httpStatusCode": 204 if http_ms is not None else None,
        "packetLossPct": packet_loss,
        "consecutiveProbeFailures": consecutive_failures,
        "usabilityScore": usability_score,
        "isValidated": is_validated,
        "isZombie": is_zombie,
        "observationQuality": quality,
        "isSynthetic": is_synthetic,
        "labelDegradation15s": label_degradation15s,
        "labelDegradation15sStatus": label_degradation15s_status,
        "labelDropout30s": label_dropout30s,
        "labelDropout30sStatus": label_dropout30s_status,
        "labelLikelyCause": label_likely_cause,
        "labelLikelyCauseStatus": label_likely_cause_status,
        "labelSchemaVersion": label_schema_version,
        "featureSchemaVersion": feature_schema_version,
    }


def make_session_window(
    session_id: str = "test-session-1",
    n_rows: int = 15,
    interval_ms: int = 1000,
    start_ts: int = 0,
    **overrides,
) -> pd.DataFrame:
    rows = [
        make_observation_row(i, session_id, start_ts + i * interval_ms, **overrides)
        for i in range(n_rows)
    ]
    return pd.DataFrame(rows)


def coerce(df: pd.DataFrame) -> pd.DataFrame:
    return dataset._coerce_types(df)
