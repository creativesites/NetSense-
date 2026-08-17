"""Tests for src/features.py - the Python mirror of PulseFeatureExtractor.kt."""

from __future__ import annotations

from src import features


def _obs(**kwargs) -> features.Observation:
    defaults = dict(
        timestamp=0, transport="WIFI", dns_latency_ms=None, tcp_rtt_ms=None,
        tcp_jitter_ms=None, http_ttfb_ms=None, packet_loss_pct=0.0, rsrp_dbm=None,
        sinr_db=None, wifi_rssi_dbm=None, consecutive_probe_failures=0,
    )
    defaults.update(kwargs)
    return features.Observation(**defaults)


def test_missing_dns_is_not_encoded_as_zero_ms():
    missing = extract0(_obs(dns_latency_ms=None))
    zero_ms = extract0(_obs(dns_latency_ms=0.0))
    assert missing == features.MISSING_VALUE_SENTINEL
    assert zero_ms == 0.0
    assert missing != zero_ms


def extract0(obs: features.Observation) -> float:
    return float(features.extract_single_step_features(obs, prev=None)[0])


def test_missing_rsrp_is_out_of_the_real_measured_range():
    missing = features.extract_single_step_features(_obs(rsrp_dbm=None), prev=None)[5]
    worst = features.extract_single_step_features(_obs(rsrp_dbm=-140.0), prev=None)[5]
    best = features.extract_single_step_features(_obs(rsrp_dbm=-44.0), prev=None)[5]

    assert missing == features.MISSING_VALUE_SENTINEL
    assert missing < 0.0 or missing > 1.0
    assert worst == 0.0
    assert best == 1.0


def test_velocity_features_are_missing_without_a_previous_observation():
    curr = _obs(timestamp=1000, tcp_rtt_ms=100.0, rsrp_dbm=-90.0)
    f = features.extract_single_step_features(curr, prev=None)
    assert f[8] == features.MISSING_VALUE_SENTINEL  # latency_velocity
    assert f[9] == features.MISSING_VALUE_SENTINEL  # signal_velocity


def test_velocity_features_are_computed_with_a_previous_observation():
    prev = _obs(timestamp=0, tcp_rtt_ms=100.0, rsrp_dbm=-90.0)
    curr = _obs(timestamp=1000, tcp_rtt_ms=200.0, rsrp_dbm=-80.0)
    f = features.extract_single_step_features(curr, prev)
    assert f[8] != features.MISSING_VALUE_SENTINEL
    assert f[9] != features.MISSING_VALUE_SENTINEL
    assert f[8] > 0.5  # latency increased -> velocity above the centered 0.5
    assert f[9] > 0.5  # signal improved -> velocity above the centered 0.5


def test_transport_encoding_matches_kotlin_constants():
    assert features.extract_single_step_features(_obs(transport="CELLULAR"), None)[11] == 0.0
    assert features.extract_single_step_features(_obs(transport="WIFI"), None)[11] == 1.0
    assert features.extract_single_step_features(_obs(transport="ETHERNET"), None)[11] == 0.5
    assert features.extract_single_step_features(_obs(transport="NONE"), None)[11] == -1.0


def test_extract_window_tensor_first_row_has_no_previous_even_with_session_history():
    """Mirrors TrainingSequenceBuilder.kt: velocity for window row 0 is always missing,
    even if earlier session rows exist outside the window - this is a real, intentional
    property of the current Android code, not a Python simplification."""
    window = [
        _obs(timestamp=i * 1000, tcp_rtt_ms=100.0 + i * 10, rsrp_dbm=-90.0)
        for i in range(features.WINDOW_SIZE)
    ]
    tensor = features.extract_window_tensor(window)
    assert tensor.shape == (features.WINDOW_SIZE, features.FEATURE_COUNT)
    assert tensor[0, 8] == features.MISSING_VALUE_SENTINEL
    assert tensor[1, 8] != features.MISSING_VALUE_SENTINEL


def test_extract_window_tensor_rejects_wrong_length():
    import pytest

    with pytest.raises(ValueError):
        features.extract_window_tensor([_obs()] * (features.WINDOW_SIZE - 1))


def test_packet_loss_and_consecutive_failures_are_never_missing():
    f = features.extract_single_step_features(_obs(packet_loss_pct=0.0, consecutive_probe_failures=0), None)
    assert f[4] == 0.0
    assert f[10] == 0.0
