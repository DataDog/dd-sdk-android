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

# Scalar metric mapping — Jetpack metric name → (cbmf_metric_name, uom)
SCALAR_METRIC_MAP: dict[str, tuple[str, str]] = {
    "timeToInitialDisplayMs": ("execution_time", "ms"),
    "timeToFullDisplayMs": ("execution_time", "ms"),
    "timeNs": ("execution_time", "ns"),
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

# Sampled metrics → use execution_time_pXX per scenario named after the metric.
# Produces one CBMF benchmark per sampled Jetpack metric (e.g.
# "frameTimingWithSessionReplay:frameDurationCpuMs"), so the BP UI shows 3 rows
# (frameDurationCpuMs, frameOverrunMs, frameCount) instead of 9.
# Each row has execution_time_p50/p95/p99 as its metrics.
# TODO: open a PR to benchmarking-platform-tools to add proper metric names
#       (e.g. agg_frame_duration_cpu_pXX) once the sprint is over.
SAMPLED_PERCENTILES = [50, 95, 99]


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

def convert_metrics(benchmark: dict, version: Optional[str] = None, extra: Optional[dict] = None) -> list[dict]:
    """Convert scalar metrics — one CBMF benchmark entry per Jetpack metric."""
    name = benchmark.get("name", "unknown")
    class_name = benchmark.get("className", "unknown")
    cbmf_benchmarks = []

    for metric_name, metric_data in benchmark.get("metrics", {}).items():
        mapping = SCALAR_METRIC_MAP.get(metric_name)
        if mapping is None:
            continue
        cbmf_name, uom = mapping
        runs_data = metric_data.get("runs", [])

        runs = {}
        for i, value in enumerate(runs_data):
            runs[f"run{i}"] = {cbmf_name: {"uom": uom, "values": [value]}}

        if runs:
            parameters = {"scenario": f"{name}:{metric_name}", "className": class_name}
            if version:
                parameters["version"] = version
            if extra:
                parameters.update(extra)
            cbmf_benchmarks.append({"parameters": parameters, "runs": runs})

    return cbmf_benchmarks


def convert_sampled_metrics(benchmark: dict, version: Optional[str] = None, extra: Optional[dict] = None) -> list[dict]:
    """Convert sampled metrics — one CBMF benchmark per metric with execution_time_pXX."""
    name = benchmark.get("name", "unknown")
    class_name = benchmark.get("className", "unknown")
    cbmf_benchmarks = []

    for metric_name, metric_data in benchmark.get("sampledMetrics", {}).items():
        runs_data = metric_data.get("runs", [])
        if not runs_data:
            continue

        runs = {}
        for i, samples in enumerate(runs_data):
            if not samples:
                continue
            sorted_samples = sorted(samples)
            runs[f"run{i}"] = {
                f"execution_time_p{p}": {"uom": "ms", "values": [_percentile(sorted_samples, p)]}
                for p in SAMPLED_PERCENTILES
            }

        if runs:
            parameters = {"scenario": f"{name}:{metric_name}", "className": class_name}
            if version:
                parameters["version"] = version
            if extra:
                parameters.update(extra)
            cbmf_benchmarks.append({"parameters": parameters, "runs": runs})

    return cbmf_benchmarks


def convert(
    jetpack_json: dict,
    version: Optional[str] = None,
    commit_sha: Optional[str] = None,
    pipeline_id: Optional[str] = None,
    branch: Optional[str] = None,
    commit_date: Optional[str] = None,
    job_id: Optional[str] = None,
    job_date: Optional[str] = None,
    cpu_model: Optional[str] = None,
) -> dict:
    """Convert a full Jetpack Benchmark JSON to CBMF v1.

    Args:
        version: Version tag to distinguish baseline from candidate.
        commit_sha: Git commit SHA embedded in parameters for BP UI traceability.
        pipeline_id: CI pipeline ID embedded in parameters for BP UI traceability.
        branch: Git branch name embedded in parameters for BP UI traceability.
        commit_date: Unix timestamp of the git commit (git log -1 --format=%ct).
        job_id: CI job ID (e.g. $CI_JOB_ID).
        job_date: Unix timestamp of when the CI job ran (date +%s).
        cpu_model: CPU model of the runner machine.
    """
    extra = {}
    if commit_sha:
        extra["git_commit_sha"] = commit_sha
    if pipeline_id:
        extra["ci_pipeline_id"] = pipeline_id
    if branch:
        extra["git_branch"] = branch
    if commit_date:
        extra["git_commit_date"] = commit_date
    if job_id:
        extra["ci_job_id"] = job_id
    if job_date:
        extra["ci_job_date"] = job_date
    if cpu_model:
        extra["cpu_model"] = cpu_model

    cbmf_benchmarks = []

    for benchmark in jetpack_json.get("benchmarks", []):
        cbmf_benchmarks.extend(convert_metrics(benchmark, version=version, extra=extra))
        cbmf_benchmarks.extend(convert_sampled_metrics(benchmark, version=version, extra=extra))

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
    parser.add_argument(
        "--version", required=True,
        help="Version tag added to each benchmark's parameters to distinguish baseline from candidate (e.g. 'baseline', 'candidate')."
    )
    parser.add_argument(
        "--commit-sha", default=None,
        help="Git commit SHA to embed in parameters for BP UI traceability (e.g. $CI_COMMIT_SHORT_SHA)."
    )
    parser.add_argument(
        "--pipeline-id", default=None,
        help="CI pipeline ID to embed in parameters for BP UI traceability (e.g. $CI_PIPELINE_ID)."
    )
    parser.add_argument(
        "--branch", default=None,
        help="Git branch name to embed in parameters for BP UI traceability (e.g. $CI_COMMIT_REF_NAME)."
    )
    parser.add_argument(
        "--commit-date", default=None,
        help="Unix timestamp of the git commit (e.g. $(git log -1 --format=%%ct))."
    )
    parser.add_argument(
        "--job-id", default=None,
        help="CI job ID (e.g. $CI_JOB_ID)."
    )
    parser.add_argument(
        "--job-date", default=None,
        help="Unix timestamp of when the CI job ran (e.g. $(date +%%s))."
    )
    parser.add_argument(
        "--cpu-model", default=None,
        help="CPU model of the runner machine (e.g. $(sysctl -n machdep.cpu.brand_string))."
    )
    args = parser.parse_args()

    with open(args.input, encoding="utf-8") as f:
        jetpack_data = json.load(f)

    cbmf = convert(
        jetpack_data,
        version=args.version,
        commit_sha=args.commit_sha,
        pipeline_id=args.pipeline_id,
        branch=args.branch,
        commit_date=args.commit_date,
        job_id=args.job_id,
        job_date=args.job_date,
        cpu_model=args.cpu_model,
    )

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
