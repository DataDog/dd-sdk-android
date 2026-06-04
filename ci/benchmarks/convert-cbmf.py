#!/usr/bin/env python3

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

"""
Convert Jetpack Macrobenchmark / Microbenchmark JSON output to CBMF v1
(Common Benchmark Measurements Format) for bp-analyzer.

Usage:
    python3 benchmark-convert-cbmf.py --input <benchmarkData.json> --output <candidate.converted.json>
    python3 benchmark-convert-cbmf.py --input <benchmarkData.json>  # prints to stdout

Input:  Jetpack Benchmark JSON produced by connectedBenchmarkAndroidTest / connectedReleaseAndroidTest.
Output: CBMF v1 JSON consumable by bp-analyzer (compare, analyze).

Reference:
    CBMF spec: https://docs.google.com/document/d/1Dlg1PIYosg5nb3EogEXLiyPuLriJjKjSU2YkGtO76vk
    Original Kotlin converter: tools/benchmark-converter (aleksandr-gringauz/jepack-benchmark-poc branch)
"""

import argparse
import json
import math
import sys
from typing import Optional

# ---------------------------------------------------------------------------
# Metric mapping — Jetpack metric name → (cbmf_metric_name, unit_of_measurement)
# ---------------------------------------------------------------------------

METRIC_MAP: dict[str, tuple[str, str]] = {
    # Timing metrics
    "timeToInitialDisplayMs": ("execution_time", "ms"),
    "timeToFullDisplayMs": ("execution_time", "ms"),
    "frameDurationCpuMs": ("execution_time", "ms"),
    "frameOverrunMs": ("execution_time", "ms"),
    "timeNs": ("execution_time", "ns"),
    # Count metrics
    "frameCount": ("iterations", "iterations"),
    "allocationCount": ("allocations", "allocations"),
    # Memory metrics
    "memoryHeapSizeMaxKb": ("rss", "KB"),
    "memoryRssAnonMaxKb": ("rss", "KB"),
    "memoryRssFileMaxKb": ("rss", "KB"),
    # Trace section metrics (TraceSectionMetric). The key is "<sectionName><Mode>Ms",
    # e.g. the Session Replay "SnapshotProducer" span with Mode.Sum -> "SnapshotProducerSumMs"
    # (total SR recording time per iteration) and Mode.Average -> "SnapshotProducerAverageMs"
    # (mean cost of a single snapshot capture, independent of frame count).
    "SnapshotProducerSumMs": ("execution_time", "ms"),
    "SnapshotProducerAverageMs": ("execution_time", "ms"),
}


SAMPLED_PERCENTILES = [50, 90, 95, 99]


def map_metric(jetpack_name: str) -> Optional[tuple[str, str]]:
    """Return (cbmf_metric_name, uom) for a Jetpack metric, or None if unmapped."""
    return METRIC_MAP.get(jetpack_name)


def _percentile(sorted_values: list[float], p: int) -> float:
    """Compute the p-th percentile using linear interpolation (same as numpy default)."""
    n = len(sorted_values)
    k = (p / 100) * (n - 1)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_values[int(k)]
    return sorted_values[f] * (c - k) + sorted_values[c] * (k - f)


# ---------------------------------------------------------------------------
# Conversion logic
# ---------------------------------------------------------------------------

def convert_metrics(benchmark: dict) -> list[dict]:
    """Convert the 'metrics' section of a Jetpack benchmark to CBMF benchmarks.

    Each Jetpack metric (e.g. frameDurationCpuMs) with its per-iteration scalar
    runs becomes a separate CBMF benchmark entry.
    """
    name = benchmark.get("name", "unknown")
    class_name = benchmark.get("className", "unknown")
    cbmf_benchmarks = []

    for metric_name, metric_data in benchmark.get("metrics", {}).items():
        mapping = map_metric(metric_name)
        if mapping is None:
            continue

        cbmf_name, uom = mapping
        runs_data = metric_data.get("runs", [])

        runs = {}
        for i, value in enumerate(runs_data):
            runs[f"run{i}"] = {
                cbmf_name: {
                    "uom": uom,
                    "values": [value],
                }
            }

        if runs:
            cbmf_benchmarks.append({
                "parameters": {
                    "scenario": f"{name}:{metric_name}",
                    "className": class_name,
                },
                "runs": runs,
            })

    return cbmf_benchmarks


def convert_sampled_metrics(benchmark: dict) -> list[dict]:
    """Convert the 'sampledMetrics' section of a Jetpack benchmark to CBMF benchmarks.

    Each sampled metric (e.g. frameDurationCpuMs) has per-iteration arrays of
    samples. Instead of passing raw arrays, we compute percentiles (P50, P90,
    P95, P99) per iteration and emit one CBMF benchmark per percentile. This
    produces better-behaved distributions for bp-analyzer comparison.
    """
    name = benchmark.get("name", "unknown")
    class_name = benchmark.get("className", "unknown")
    cbmf_benchmarks = []

    for metric_name, metric_data in benchmark.get("sampledMetrics", {}).items():
        mapping = map_metric(metric_name)
        if mapping is None:
            continue

        cbmf_name, uom = mapping
        runs_data = metric_data.get("runs", [])

        for p in SAMPLED_PERCENTILES:
            runs = {}
            for i, samples in enumerate(runs_data):
                if not samples:
                    continue
                sorted_samples = sorted(samples)
                value = _percentile(sorted_samples, p)
                runs[f"run{i}"] = {
                    cbmf_name: {
                        "uom": uom,
                        "values": [value],
                    }
                }

            if runs:
                cbmf_benchmarks.append({
                    "parameters": {
                        "scenario": f"{name}:{metric_name}:P{p}",
                        "className": class_name,
                    },
                    "runs": runs,
                })

    return cbmf_benchmarks


def convert(jetpack_json: dict) -> dict:
    """Convert a full Jetpack Benchmark JSON to CBMF v1."""
    cbmf_benchmarks = []

    for benchmark in jetpack_json.get("benchmarks", []):
        cbmf_benchmarks.extend(convert_metrics(benchmark))
        cbmf_benchmarks.extend(convert_sampled_metrics(benchmark))

    return {
        "schema_version": "v1",
        "benchmarks": cbmf_benchmarks,
    }


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Convert Jetpack Benchmark JSON to CBMF v1 for bp-analyzer."
    )
    parser.add_argument(
        "--input", required=True,
        help="Path to the Jetpack Benchmark JSON file (benchmarkData.json)."
    )
    parser.add_argument(
        "--output", default=None,
        help="Path to write the CBMF JSON output. Prints to stdout if omitted."
    )
    args = parser.parse_args()

    with open(args.input, encoding="utf-8") as f:
        jetpack_data = json.load(f)

    cbmf = convert(jetpack_data)

    if not cbmf["benchmarks"]:
        print("Warning: no benchmarks were converted. Check that the input file "
              "contains recognized metrics.", file=sys.stderr)
        sys.exit(1)

    output_json = json.dumps(cbmf, indent=2)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(output_json)
            f.write("\n")
        print(f"Converted {len(cbmf['benchmarks'])} benchmark(s) → {args.output}",
              file=sys.stderr)
    else:
        print(output_json)


if __name__ == "__main__":
    main()
