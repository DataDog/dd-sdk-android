#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

docker run --rm --name benchmark_analyzer -it \
  -v$(pwd):/data:rw \
  registry.ddbuild.io/images/benchmark-analyzer \
  analyze \
  --format=html \
  --outpath="summary.html" \
  "some_results.json"
