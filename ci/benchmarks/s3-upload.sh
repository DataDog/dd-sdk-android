#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Upload Jetpack Macrobenchmark results to the Benchmarking Platform S3 bucket.
#
# NOTE: This runs on the macOS runner as part of benchmark:macro to keep things
# simple — the runner already has AWS credentials via vault login -method=aws,
# so no extra setup is needed. If macOS runner costs become a concern or we add
# more post-processing steps (e.g. bp-analyzer comparison), consider moving this
# to a separate amd64 job that consumes GitLab CI artifacts instead.

set -euo pipefail

S3_BASE="s3://relenv-benchmarking-data/dd-sdk-android/${CI_COMMIT_REF_SLUG}/${CI_JOB_ID}"

BENCHMARK_JSON=$(find benchmarks/macro/build/outputs/ -name "*benchmarkData.json" | head -1)

if [ -z "$BENCHMARK_JSON" ]; then
  echo "Warning: benchmarkData.json not found, skipping S3 upload." >&2
  exit 0
fi

echo "Converting ${BENCHMARK_JSON} to CBMF..."
python3 ci/benchmarks/convert-cbmf.py \
  --input "$BENCHMARK_JSON" \
  --version candidate \
  --output macrobenchmark-candidate.cbmf.json

echo "Uploading to ${S3_BASE}..."
aws s3 cp "$BENCHMARK_JSON"                  "${S3_BASE}/macrobenchmark-raw.json"
aws s3 cp macrobenchmark-candidate.cbmf.json "${S3_BASE}/macrobenchmark-candidate.cbmf.json"

echo "Upload complete: ${S3_BASE}"
