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

bp-analyzer compare pairwise \
  --format=md \
  --outpath=benchmark-comparison.md \
  macrobenchmark-baseline.cbmf.json \
  macrobenchmark-candidate.cbmf.json

echo "Comparison report saved to benchmark-comparison.md"
