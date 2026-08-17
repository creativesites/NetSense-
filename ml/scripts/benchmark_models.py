#!/usr/bin/env python3
"""
Phase 12/13/14: model size (FP32/FP16/INT8-dynamic-range) and a documented Android
benchmark SPECIFICATION.

IMPORTANT HONESTY NOTE:
- Architecture SIZE (parameter count, TFLite FP32/FP16/dynamic-range-INT8 byte size) is a
  static property of a model's architecture and CAN be measured even before real training
  data exists (an untrained model has the same number of bytes as a trained one). This
  script genuinely measures those sizes.
- Full INTEGER quantization (weights AND activations) requires a REPRESENTATIVE DATASET
  for calibration. We do not have real telemetry, so this script only performs DYNAMIC
  RANGE INT8 quantization (weights-only), and says so explicitly rather than silently
  reporting a number under the "INT8" label that implies full calibration happened.
- Inference LATENCY numbers this script produces are measured on THIS machine's CPU (a
  cloud dev container), not a Samsung Galaxy S20 or any Android device. They are printed
  with an explicit "HOST CPU ONLY - NOT REPRESENTATIVE OF ANDROID HARDWARE" label. No S20
  number is ever invented - see the printed ANDROID BENCHMARK SPECIFICATION for what a
  real device measurement will require later.

Usage:
    python scripts/benchmark_models.py --variant cnn_multi_task
"""

from __future__ import annotations

import argparse
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import numpy as np

from src import models


def build_model_for_variant(variant: str):
    if variant == "mlp":
        return models.build_mlp(n_outputs=1)
    if variant == "cnn_single":
        return models.build_single_task_cnn("dropout30s")
    if variant == "cnn_multi_task":
        heads = models.CnnHeadsConfig(dropout30s=True, degradation15s=True, likely_cause=False, anomaly=False)
        return models.build_cnn(heads)
    raise ValueError(f"unknown variant {variant!r}")


def tflite_size_bytes(model, quantize: str) -> int:
    """quantize: 'fp32' | 'fp16' | 'int8_dynamic_range'."""
    import tensorflow as tf

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if quantize == "fp16":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    elif quantize == "int8_dynamic_range":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        # No representative_dataset provided (we have no real telemetry to calibrate with) -
        # this yields WEIGHTS-ONLY dynamic-range int8, not full integer quantization.
    elif quantize != "fp32":
        raise ValueError(f"unknown quantize mode {quantize!r}")

    tflite_bytes = converter.convert()
    return len(tflite_bytes)


def host_cpu_latency_ms(model, n_runs: int = 50) -> dict:
    """Measures inference latency on THIS host's CPU only. Explicitly not a proxy for any
    Android device - see the module docstring."""
    dummy_input = np.random.default_rng(0).uniform(0, 1, size=(1, 15, 12)).astype(np.float32)

    # warm-up
    for _ in range(5):
        model.predict(dummy_input, verbose=0)

    timings = []
    for _ in range(n_runs):
        start = time.perf_counter()
        model.predict(dummy_input, verbose=0)
        timings.append((time.perf_counter() - start) * 1000.0)

    return {
        "n_runs": n_runs,
        "mean_ms": float(np.mean(timings)),
        "p50_ms": float(np.median(timings)),
        "p95_ms": float(np.percentile(timings, 95)),
        "note": "HOST CPU ONLY (dev container) - NOT REPRESENTATIVE OF ANDROID/S20 HARDWARE. "
        "Keras .predict() call overhead dominates at this batch size; a real benchmark "
        "would use the converted .tflite Interpreter directly.",
    }


ANDROID_BENCHMARK_SPECIFICATION = """
ANDROID BENCHMARK SPECIFICATION (Phase 14) - methodology for LATER execution on real
hardware. No numbers below are measured; this documents HOW they will be measured once a
candidate .tflite model and a physical (or emulated) Samsung Galaxy S20-class device are
available. This script does NOT perform any of these measurements.

1. Model load time
   - Wrap `Interpreter(modelBuffer, options)` construction in
     PulsePredictorEngine.loadModelIfAvailable() with System.currentTimeMillis() timing
     (the field `modelLoadDurationMs` already exists for this).
   - Report cold start (first app launch) vs warm (subsequent) load time separately.

2. Inference latency
   - Use PulsePredictorEngine.benchmark() (already implemented) which times
     interpreter.runForMultipleInputsOutputs() over 5 repeated runs.
   - Run on-device via `adb shell am instrument` or a local Robolectric-free instrumented
     test, on an actual S20 (Snapdragon 865 / Exynos 990) or an AVD profile matching it.
   - Report p50/p95/p99 over >=100 runs, both cold (first inference) and warm.

3. Peak memory
   - `adb shell dumpsys meminfo <package>` before/during/after inference, or Android
     Studio's Memory Profiler capturing a heap dump around the inference call.

4. CPU utilization
   - `adb shell top -m 10 -d 1` sampled during a burst of inferences, or Perfetto/Android
     GPU Inspector system trace capturing per-core utilization attributed to the app process.

5. Approximate battery impact
   - `adb shell dumpsys batterystats` delta across a fixed-duration test session with the
     Sentinel-driven inference cadence active vs. inactive (A/B), using Android's Battery
     Historian to attribute wakeups/CPU time to the app.

Target hardware: Samsung Galaxy S20-class (Snapdragon 865 / Exynos 990, 8-12GB RAM).
This script and this ML lab environment cannot perform any of the above - there is no
Android device or emulator attached to this offline ML environment by design (Phase 24:
production boundary - ML experimentation stays offline until a model is selected).
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", choices=["mlp", "cnn_single", "cnn_multi_task"], required=True)
    parser.add_argument("--skip-host-latency", action="store_true")
    args = parser.parse_args()

    model = build_model_for_variant(args.variant)
    param_count = models.keras_param_count(model)

    print(f"=== {args.variant} ===")
    print(f"Trainable parameters: {param_count}")

    for mode, label in (("fp32", "FP32"), ("fp16", "FP16"), ("int8_dynamic_range", "INT8 (dynamic range, weights-only - no calibration data available)")):
        try:
            size = tflite_size_bytes(model, mode)
            print(f"{label} TFLite size: {size} bytes ({size / 1024:.1f} KB)")
        except Exception as e:  # pragma: no cover - conversion environment issues shouldn't crash the whole script
            print(f"{label} TFLite conversion failed: {e}", file=sys.stderr)

    if not args.skip_host_latency:
        latency = host_cpu_latency_ms(model)
        print(f"\nHost CPU latency (NOT Android/S20): mean={latency['mean_ms']:.2f}ms "
              f"p50={latency['p50_ms']:.2f}ms p95={latency['p95_ms']:.2f}ms")
        print(f"NOTE: {latency['note']}")

    print(ANDROID_BENCHMARK_SPECIFICATION)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
