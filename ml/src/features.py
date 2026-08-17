"""
Python mirror of PulseFeatureExtractor.kt / PulsePredictorConfig.kt.

Every constant and formula here MUST match the Kotlin source exactly:
  app/src/main/java/com/netsense/netpulse/ai/predictor/PulseFeatureExtractor.kt
  app/src/main/java/com/netsense/netpulse/ai/predictor/PulsePredictorConfig.kt
  app/src/main/java/com/netsense/netpulse/ai/predictor/PulseFeatureSchema.kt

If the Kotlin feature extractor changes, this file and FEATURE_SCHEMA_VERSION below must
change in the same commit, or offline-trained models will silently no longer match what the
Android app actually feeds a TFLite model at inference time.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

WINDOW_SIZE = 15  # PulsePredictorConfig.WINDOW_SIZE
FEATURE_COUNT = 12  # PulsePredictorConfig.FEATURE_COUNT
FEATURE_SCHEMA_VERSION = 2  # PulsePredictorConfig.FEATURE_NORMALIZATION_VERSION

# PulseFeatureSchema.MISSING_VALUE_SENTINEL - out-of-[0,1]-range so it can never collide
# with a genuine measured reading.
MISSING_VALUE_SENTINEL = -1.0

FEATURE_NAMES = [
    "dns_latency",
    "tcp_rtt",
    "tcp_jitter",
    "http_ttfb",
    "packet_loss",
    "cellular_rsrp",
    "cellular_sinr",
    "wifi_rssi",
    "latency_velocity",
    "signal_velocity",
    "consecutive_failures",
    "transport_type",
]

# Normalization ranges - PulsePredictorConfig.*_MIN_*/ *_MAX_*
DNS_LATENCY_MIN_MS, DNS_LATENCY_MAX_MS = 0.0, 1000.0
TCP_RTT_MIN_MS, TCP_RTT_MAX_MS = 0.0, 1500.0
TCP_JITTER_MIN_MS, TCP_JITTER_MAX_MS = 0.0, 500.0
HTTP_TTFB_MIN_MS, HTTP_TTFB_MAX_MS = 0.0, 2000.0
RSRP_MIN_DBM, RSRP_MAX_DBM = -140.0, -44.0
SINR_MIN_DB, SINR_MAX_DB = -10.0, 30.0
WIFI_RSSI_MIN_DBM, WIFI_RSSI_MAX_DBM = -100.0, -30.0
LATENCY_VELOCITY_MIN, LATENCY_VELOCITY_MAX = -500.0, 500.0
SIGNAL_VELOCITY_MIN, SIGNAL_VELOCITY_MAX = -30.0, 30.0
MAX_CONSECUTIVE_FAILURES = 5.0

TRANSPORT_ENCODING = {
    "CELLULAR": 0.0,
    "WIFI": 1.0,
    "ETHERNET": 0.5,
    "BLUETOOTH": 0.5,
    "VPN": 0.5,
    "OTHER": 0.5,
    "NONE": -1.0,
}


def _normalize(value: float | None, lo: float, hi: float) -> float:
    """Mirrors PulseFeatureExtractor.normalize(): MISSING_VALUE_SENTINEL for None, else min-max to [0,1]."""
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return MISSING_VALUE_SENTINEL
    clamped = min(max(float(value), lo), hi)
    return (clamped - lo) / (hi - lo)


@dataclass
class Observation:
    """Minimal shape needed to compute one step's features - mirrors RawTelemetryObservation."""

    timestamp: int
    transport: str
    dns_latency_ms: float | None
    tcp_rtt_ms: float | None
    tcp_jitter_ms: float | None
    http_ttfb_ms: float | None
    packet_loss_pct: float
    rsrp_dbm: float | None
    sinr_db: float | None
    wifi_rssi_dbm: float | None
    consecutive_probe_failures: int

    @staticmethod
    def from_row(row: pd.Series) -> "Observation":
        return Observation(
            timestamp=int(row["timestamp"]),
            transport=str(row["transport"]),
            dns_latency_ms=_none_if_nan(row.get("dnsLatencyMs")),
            tcp_rtt_ms=_none_if_nan(row.get("tcpRttMs")),
            tcp_jitter_ms=_none_if_nan(row.get("tcpJitterMs")),
            http_ttfb_ms=_none_if_nan(row.get("httpTtfbMs")),
            packet_loss_pct=float(row.get("packetLossPct") or 0.0),
            rsrp_dbm=_none_if_nan(row.get("rsrpDbm")),
            sinr_db=_none_if_nan(row.get("sinrDb")),
            wifi_rssi_dbm=_none_if_nan(row.get("wifiRssiDbm")),
            consecutive_probe_failures=int(row.get("consecutiveProbeFailures") or 0),
        )


def _none_if_nan(value) -> float | None:
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    return float(value)


def extract_single_step_features(curr: Observation, prev: Observation | None) -> np.ndarray:
    """Mirrors PulseFeatureExtractor.extractSingleStepFeatures exactly, feature-for-feature."""
    f = np.empty(FEATURE_COUNT, dtype=np.float32)

    f[0] = _normalize(curr.dns_latency_ms, DNS_LATENCY_MIN_MS, DNS_LATENCY_MAX_MS)
    f[1] = _normalize(curr.tcp_rtt_ms, TCP_RTT_MIN_MS, TCP_RTT_MAX_MS)
    f[2] = _normalize(curr.tcp_jitter_ms, TCP_JITTER_MIN_MS, TCP_JITTER_MAX_MS)
    f[3] = _normalize(curr.http_ttfb_ms, HTTP_TTFB_MIN_MS, HTTP_TTFB_MAX_MS)
    f[4] = min(max(curr.packet_loss_pct, 0.0), 1.0)  # never missing
    f[5] = _normalize(curr.rsrp_dbm, RSRP_MIN_DBM, RSRP_MAX_DBM)
    f[6] = _normalize(curr.sinr_db, SINR_MIN_DB, SINR_MAX_DB)
    f[7] = _normalize(curr.wifi_rssi_dbm, WIFI_RSSI_MIN_DBM, WIFI_RSSI_MAX_DBM)

    # Latency velocity: None (-> sentinel) unless both prev and both RTT readings exist.
    latency_velocity = None
    if prev is not None and curr.tcp_rtt_ms is not None and prev.tcp_rtt_ms is not None:
        dt_sec = max((curr.timestamp - prev.timestamp) / 1000.0, 0.5)
        latency_velocity = (curr.tcp_rtt_ms - prev.tcp_rtt_ms) / dt_sec
    f[8] = _normalize(latency_velocity, LATENCY_VELOCITY_MIN, LATENCY_VELOCITY_MAX)

    # Signal velocity: rsrp takes precedence over wifi rssi, matching `curr.rsrpDbm ?: curr.wifiRssiDbm`.
    signal_velocity = None
    if prev is not None:
        curr_sig = curr.rsrp_dbm if curr.rsrp_dbm is not None else curr.wifi_rssi_dbm
        prev_sig = prev.rsrp_dbm if prev.rsrp_dbm is not None else prev.wifi_rssi_dbm
        if curr_sig is not None and prev_sig is not None:
            dt_sec = max((curr.timestamp - prev.timestamp) / 1000.0, 0.5)
            signal_velocity = (curr_sig - prev_sig) / dt_sec
    f[9] = _normalize(signal_velocity, SIGNAL_VELOCITY_MIN, SIGNAL_VELOCITY_MAX)

    f[10] = min(max(curr.consecutive_probe_failures / MAX_CONSECUTIVE_FAILURES, 0.0), 1.0)
    f[11] = TRANSPORT_ENCODING.get(curr.transport, TRANSPORT_ENCODING["OTHER"])

    return f


def extract_window_tensor(window_rows: list[Observation]) -> np.ndarray:
    """Mirrors PulseFeatureExtractor.extractTensor: prev is null for the FIRST row of the
    window even if earlier session history exists outside it - this is the real, current
    Android behavior (TrainingSequenceBuilder/TelemetryWindow both slice to WINDOW_SIZE
    before computing velocities), not a Python-side simplification. See the leakage/feature
    quality audit for why this matters."""
    if len(window_rows) != WINDOW_SIZE:
        raise ValueError(f"expected exactly {WINDOW_SIZE} rows, got {len(window_rows)}")
    matrix = np.empty((WINDOW_SIZE, FEATURE_COUNT), dtype=np.float32)
    for i, curr in enumerate(window_rows):
        prev = window_rows[i - 1] if i > 0 else None
        matrix[i] = extract_single_step_features(curr, prev)
    return matrix
