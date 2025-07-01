#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

#resultsPath="/Users/aleksandr.gringauz/projects/dd-sdk-android/macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/Pixel 5 - 13/com.datadog.android.macrobenchmark-benchmarkData.json"
resultsPath="/Users/aleksandr.gringauz/projects/dd-sdk-android/microbenchmark/build/outputs/connected_android_test_additional_output/releaseAndroidTest/connected/Pixel 5 - 13/com.datadog.android.macrobenchmark.test-benchmarkData.json"

#./gradlew -q :tools:benchmark-converter:run --args="--resultPath '$resultsPath'" > results_microbenchmark.json

docker run --rm --name benchmark_analyzer -it \
  -v$(pwd):/data:rw \
  registry.ddbuild.io/images/benchmark-analyzer \
  analyze \
  --format=html \
  --outpath="summary.html" \
  "results_microbenchmark.json"


#docker run --rm --name benchmark_analyzer -it \
#  -v$(pwd):/data:rw \
#  registry.ddbuild.io/images/benchmark-analyzer \
#  compare pairwise \
#  --format=html \
#  --outpath="summary_microbenchmark_tracing.html" \
#  "results_baseline.json" \
#  "results_instrumented_sr.json"
