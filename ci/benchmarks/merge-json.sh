#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Merge multiple Jetpack Benchmark JSON files into a single combined file.
# Each input file must have a top-level "benchmarks" array.
#
# Usage:
#   bash ci/benchmarks/merge-json.sh -o output.json input1.json input2.json ...
#   bash ci/benchmarks/merge-json.sh -o output.json --glob 'path/**/*benchmarkData.json'

set -euo pipefail

OUTPUT=""
INPUTS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    -o|--output)
      OUTPUT="$2"
      shift 2
      ;;
    --glob)
      shopt -s nullglob globstar
      INPUTS+=($(eval echo "$2"))
      shopt -u nullglob globstar
      shift 2
      ;;
    *)
      INPUTS+=("$1")
      shift
      ;;
  esac
done

if [ -z "$OUTPUT" ]; then
  echo "Error: -o/--output is required." >&2
  exit 1
fi

if [ ${#INPUTS[@]} -eq 0 ]; then
  echo "Warning: no input files provided or matched, skipping merge." >&2
  exit 0
fi

python3 -c "
import json, sys

output_path = sys.argv[1]
input_paths = sys.argv[2:]

combined = {'benchmarks': []}
for path in input_paths:
    with open(path) as f:
        data = json.load(f)
    count = len(data.get('benchmarks', []))
    combined['benchmarks'].extend(data.get('benchmarks', []))
    print(f'  {path}: {count} benchmark(s)', file=sys.stderr)

print(f'Merged {len(combined[\"benchmarks\"])} benchmark(s) from {len(input_paths)} file(s) -> {output_path}', file=sys.stderr)

with open(output_path, 'w') as f:
    json.dump(combined, f, indent=2)
" "$OUTPUT" "${INPUTS[@]}"
