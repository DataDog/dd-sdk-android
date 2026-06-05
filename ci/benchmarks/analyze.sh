#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Merge macro + micro benchmark raw JSONs, upload to S3, convert to CBMF,
# run bp-analyzer pairwise comparison, and upload to Benchmarking Platform UI.
#
# Expects artifacts from benchmark:macro (macrobenchmark-raw.json) and/or
# benchmark:micro (microbenchmark-raw.json). At least one must be present.
#
# NOTE: PR comment posting is not implemented yet — will be added in a future iteration.

set -euo pipefail

S3_LATEST_BASE="s3://relenv-benchmarking-data/dd-sdk-android/_latest"
COMMITTED_BASELINE="ci/benchmarks/macrobenchmark-baseline.cbmf.json"

# ---------------------------------------------------------------------------
# 1. Merge raw JSONs and upload to S3
# ---------------------------------------------------------------------------
bash ci/benchmarks/s3-upload.sh

if [ ! -f benchmark-raw.json ]; then
  echo "Error: no benchmark-raw.json produced, aborting." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 2. Convert candidate raw JSON → CBMF
# ---------------------------------------------------------------------------
echo "Converting candidate results to CBMF..."

# Use "develop" as branch name when running from the test branch so BP UI
# groups results together with the develop baseline (revert before merging).
CANDIDATE_BRANCH="${CI_COMMIT_REF_NAME}"
if [ "${CI_COMMIT_REF_NAME}" = "aleksandr-gringauz/jetback-benchmarks-on-ci" ]; then
  CANDIDATE_BRANCH="develop"
fi

python3 ci/benchmarks/convert-cbmf.py \
  --input benchmark-raw.json \
  --version candidate \
  --commit-sha "${CI_COMMIT_SHORT_SHA}" \
  --pipeline-id "${CI_PIPELINE_ID}" \
  --branch "${CANDIDATE_BRANCH}" \
  --commit-date "$(git log -1 --format=%ct)" \
  --job-id "${CI_JOB_ID}" \
  --job-date "$(date +%s)" \
  --cpu-model "$(uname -m)" \
  --output candidate-dd-sdk-android.converted.json

# ---------------------------------------------------------------------------
# 3. Download baseline from S3 _latest and convert, or use committed fallback
# ---------------------------------------------------------------------------
if aws s3 cp "${S3_LATEST_BASE}/benchmark-raw.json" baseline-raw.json 2>/dev/null; then
  echo "Baseline raw JSON downloaded from S3."

  if aws s3 cp "${S3_LATEST_BASE}/env_vars.txt" baseline_env_vars.txt 2>/dev/null; then
    while IFS='=' read -r key value; do
      [ -n "$key" ] && export "BASELINE_${key}=${value}"
    done < baseline_env_vars.txt
    echo "Loaded baseline env vars: BASELINE_CI_COMMIT_SHORT_SHA=${BASELINE_CI_COMMIT_SHORT_SHA:-unset}"
  fi

  echo "Converting baseline results to CBMF..."
  python3 ci/benchmarks/convert-cbmf.py \
    --input baseline-raw.json \
    --version baseline \
    --commit-sha "${BASELINE_CI_COMMIT_SHORT_SHA:-unknown}" \
    --pipeline-id "${BASELINE_CI_PIPELINE_ID:-unknown}" \
    --branch "develop" \
    --job-id "${BASELINE_CI_JOB_ID:-unknown}" \
    --job-date "${BASELINE_CI_JOB_DATE:-0}" \
    --cpu-model "$(uname -m)" \
    --output baseline-dd-sdk-android.converted.json
else
  echo "No S3 baseline found, using committed fallback."
  cp "$COMMITTED_BASELINE" baseline-dd-sdk-android.converted.json
fi

# ---------------------------------------------------------------------------
# 4. Run bp-analyzer pairwise comparison
# ---------------------------------------------------------------------------
export MD_REPORT_ONLY_CHANGES=1

bp-analyzer compare pairwise \
  --format=md \
  --outpath=benchmark-comparison.md \
  --baseline='{"version": "baseline"}' \
  --candidate='{"version": "candidate"}' \
  baseline-dd-sdk-android.converted.json \
  candidate-dd-sdk-android.converted.json

echo "Comparison report saved to benchmark-comparison.md"

# ---------------------------------------------------------------------------
# 5. Upload to Benchmarking Platform UI
# ---------------------------------------------------------------------------
bash ci/benchmarks/bp-upload.sh || echo "Warning: BP upload failed, continuing."

# ---------------------------------------------------------------------------
# 6. Post PR comment
# ---------------------------------------------------------------------------
bash ci/benchmarks/post-pr-comment.sh || echo "Warning: PR comment failed, continuing."
