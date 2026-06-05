#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Upload Jetpack Macrobenchmark raw results to the Benchmarking Platform S3 bucket.
# Conversion to CBMF happens later on the amd64 benchmark:analyze job.
#
# NOTE: This runs on the macOS runner as part of benchmark:macro to keep things
# simple — the runner already has AWS credentials via vault login -method=aws,
# so no extra setup is needed. If macOS runner costs become a concern or we add
# more post-processing steps, consider moving this to a separate amd64 job that
# consumes GitLab CI artifacts instead.

set -euo pipefail

S3_BASE="s3://relenv-benchmarking-data/dd-sdk-android/${CI_COMMIT_REF_SLUG}/${CI_JOB_ID}"

BENCHMARK_JSON=$(find benchmarks/macro/build/outputs/ -name "*benchmarkData.json" | head -1)

if [ -z "$BENCHMARK_JSON" ]; then
  echo "Warning: benchmarkData.json not found, skipping S3 upload." >&2
  exit 0
fi

# Copy raw JSON to a fixed name for use as GitLab artifact
cp "$BENCHMARK_JSON" macrobenchmark-raw.json

echo "Uploading to ${S3_BASE}..."
aws s3 cp macrobenchmark-raw.json "${S3_BASE}/macrobenchmark-raw.json"
echo "Upload complete: ${S3_BASE}"

# Promote to _latest on develop merges so PR runs have a baseline to compare against
if [ "${CI_COMMIT_REF_NAME}" = "develop" ]; then
  echo "Promoting results to _latest..."
  S3_LATEST="s3://relenv-benchmarking-data/dd-sdk-android/_latest"

  aws s3 cp macrobenchmark-raw.json "${S3_LATEST}/macrobenchmark-raw.json"

  cat > env_vars.txt <<EOF
CI_COMMIT_SHORT_SHA=${CI_COMMIT_SHORT_SHA}
CI_PIPELINE_ID=${CI_PIPELINE_ID}
CI_JOB_ID=${CI_JOB_ID}
CI_JOB_DATE=$(date +%s)
EOF
  aws s3 cp env_vars.txt "${S3_LATEST}/env_vars.txt"
  echo "_latest promotion complete: ${S3_LATEST}"
fi
