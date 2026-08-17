#!/usr/bin/env python3
"""
Builds windowed training sequences from a real dataset export and caches them as .npz
(one array per task) so train_baselines.py/train_cnn.py don't need to re-parse/re-window
the raw export on every run.

Usage:
    python scripts/build_sequences.py --input data/export.jsonl --output data/sequences.npz
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import numpy as np

from src import dataset, sequences


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    input_path = Path(args.input)
    loader = dataset.load_jsonl if input_path.suffix.lower() == ".jsonl" else dataset.load_csv
    try:
        df = loader(input_path)
    except FileNotFoundError as e:
        print(f"NO DATA: {e}", file=sys.stderr)
        return 2

    seqs = sequences.build_sequences(df)
    if not seqs:
        print(
            "No training sequences could be built (need >=15 consecutive, session-safe, "
            "non-synthetic, non-INVALID observations in at least one session). Nothing to save.",
            file=sys.stderr,
        )
        return 1

    X_degradation, y_degradation, sessions_degradation = sequences.sequences_to_arrays(seqs, "degradation15s")
    X_dropout, y_dropout, sessions_dropout = sequences.sequences_to_arrays(seqs, "dropout30s")

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(
        output_path,
        X_degradation15s=X_degradation,
        y_degradation15s=y_degradation,
        sessions_degradation15s=np.array(sessions_degradation),
        X_dropout30s=X_dropout,
        y_dropout30s=y_dropout,
        sessions_dropout30s=np.array(sessions_dropout),
    )

    print(f"Built {len(seqs)} total windowed sequences.")
    print(f"  degradation15s RESOLVED sequences usable for training: {len(y_degradation)}")
    print(f"  dropout30s RESOLVED sequences usable for training: {len(y_dropout)}")
    print(f"Saved to {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
