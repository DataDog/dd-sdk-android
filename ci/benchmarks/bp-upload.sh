#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Upload CBMF benchmark results to the Benchmarking Platform UI.
#
# Follows the same pattern as dd-trace-dotnet/scripts/upload-to-bp-ui.sh.
# The project name is derived from $CI_PROJECT_NAME (automatically set by GitLab CI).
#
# Expected input files: candidate-*.converted.json in the current directory.

set -euo pipefail

ARTIFACTS_DIR="${ARTIFACTS_DIR:-$(pwd)}"

shopt -s nullglob
converted_files=("$ARTIFACTS_DIR"/candidate*.converted.json)
shopt -u nullglob

if [ ${#converted_files[@]} -eq 0 ]; then
  echo "No candidate*.converted.json files found, skipping BP upload." >&2
  exit 0
fi

for file in "${converted_files[@]}"; do
  echo "Uploading $(basename "$file") to Benchmarking Platform (project: ${CI_PROJECT_NAME})..."

  status=$(curl --retry 3 --retry-all-errors --retry-max-time 300 \
    -s -w "%{http_code}" --output /dev/stderr \
    --form file=@"$file" \
    "https://benchmarking-service.us1.prod.dog/benchmarks/upload/${CI_PROJECT_NAME}")

  if [ "$status" -ne 200 ]; then
    echo "Warning: BP upload failed with status $status, continuing." >&2
  else
    echo "Uploaded successfully. View results at: https://benchmarking.us1.prod.dog"
  fi
done
