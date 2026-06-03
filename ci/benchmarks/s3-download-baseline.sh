#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Download the baseline CBMF file from S3 for pairwise comparison.
#
# Tries S3 _latest first. Falls back to the committed baseline file if S3 has
# no _latest yet (e.g. on the first run before any develop merge has promoted
# a baseline). In both cases the result is written to macrobenchmark-baseline.cbmf.json.
#
# NOTE: _latest is not yet populated automatically. A future iteration will add
# a benchmark:promote-baseline job that runs on develop merges and copies the
# current results to _latest. Until then, the committed fallback is always used.

set -euo pipefail

S3_LATEST="s3://relenv-benchmarking-data/dd-sdk-android/_latest/macrobenchmark-baseline.cbmf.json"
FALLBACK="ci/benchmarks/macrobenchmark-baseline.cbmf.json"
OUTPUT="macrobenchmark-baseline.cbmf.json"

if aws s3 cp "$S3_LATEST" "$OUTPUT" 2>/dev/null; then
  echo "Baseline downloaded from S3: ${S3_LATEST}"
else
  echo "No S3 baseline found at ${S3_LATEST}, using committed fallback."
  cp "$FALLBACK" "$OUTPUT"
fi
