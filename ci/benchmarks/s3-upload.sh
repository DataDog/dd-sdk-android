#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Merge available Jetpack benchmark raw JSONs (macro + micro) into a single
# benchmark-raw.json file, upload it to the Benchmarking Platform S3 bucket,
# and promote to _latest on baseline branches.
#
# Called from the benchmark:analyze job which has artifacts from both
# benchmark:macro and benchmark:micro (micro is optional).

set -euo pipefail

S3_BASE="s3://relenv-benchmarking-data/dd-sdk-android/${CI_COMMIT_REF_SLUG}/${CI_PIPELINE_ID}"

# ---------------------------------------------------------------------------
# 1. Merge available raw JSONs into benchmark-raw.json
# ---------------------------------------------------------------------------
INPUT_FILES=()
[ -f macrobenchmark-raw.json ] && INPUT_FILES+=(macrobenchmark-raw.json)
[ -f microbenchmark-raw.json ] && INPUT_FILES+=(microbenchmark-raw.json)

if [ ${#INPUT_FILES[@]} -eq 0 ]; then
  echo "Warning: no benchmark raw JSON files found, skipping S3 upload." >&2
  exit 0
fi

bash ci/benchmarks/merge-json.sh -o benchmark-raw.json "${INPUT_FILES[@]}"

# ---------------------------------------------------------------------------
# 2. Upload to S3
# ---------------------------------------------------------------------------
echo "Uploading to ${S3_BASE}..."
aws s3 cp benchmark-raw.json "${S3_BASE}/benchmark-raw.json"
echo "Upload complete: ${S3_BASE}"

# ---------------------------------------------------------------------------
# 3. Promote to _latest on baseline branches
# ---------------------------------------------------------------------------
# TEMPORARY: also promote from test branch to seed baseline (revert before merging to develop)
if [ "${CI_COMMIT_REF_NAME}" = "develop" ] || [ "${CI_COMMIT_REF_NAME}" = "aleksandr-gringauz/jetback-benchmarks-on-ci" ]; then
  echo "Promoting results to _latest..."
  S3_LATEST="s3://relenv-benchmarking-data/dd-sdk-android/_latest"

  aws s3 cp benchmark-raw.json "${S3_LATEST}/benchmark-raw.json"

  cat > env_vars.txt <<EOF
CI_COMMIT_SHORT_SHA=${CI_COMMIT_SHORT_SHA}
CI_PIPELINE_ID=${CI_PIPELINE_ID}
CI_JOB_DATE=$(date +%s)
EOF
  aws s3 cp env_vars.txt "${S3_LATEST}/env_vars.txt"
  echo "_latest promotion complete: ${S3_LATEST}"
fi
