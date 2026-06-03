#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Run bp-analyzer pairwise comparison between baseline and candidate CBMF files.
# Output is saved as benchmark-comparison.md, available as a GitLab artifact for
# manual inspection.
#
# NOTE: PR comment posting is not implemented yet — will be added in a future iteration.

set -euo pipefail

export MD_REPORT_ONLY_CHANGES=1

# bp-analyzer pairwise pairs benchmarks by "scenario" and tells the two sides
# apart via a distinguishing parameter selected with --baseline/--candidate.
# The baseline CBMF is just a previously promoted candidate, so we force-set the
# "variant" parameter on each file here to guarantee the two sides differ
# (otherwise identical parameters get merged and the comparison finds no pairs).
tag_variant() {
  local file="$1"
  local variant="$2"
  python3 - "$file" "$variant" <<'PY'
import json
import sys

path, variant = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    data = json.load(f)
for benchmark in data.get("benchmarks", []):
    benchmark.setdefault("parameters", {})["variant"] = variant
with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, indent=2)
PY
}

tag_variant macrobenchmark-baseline.cbmf.json baseline
tag_variant macrobenchmark-candidate.cbmf.json candidate

bp-analyzer compare pairwise \
  --format=md \
  --outpath=benchmark-comparison.md \
  --baseline='{"variant":"baseline"}' \
  --candidate='{"variant":"candidate"}' \
  macrobenchmark-baseline.cbmf.json \
  macrobenchmark-candidate.cbmf.json

echo "Comparison report saved to benchmark-comparison.md"
