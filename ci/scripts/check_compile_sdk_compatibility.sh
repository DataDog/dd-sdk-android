#!/usr/bin/env bash
#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

set -euo pipefail

echo "Building the integration app at compileSdk 35"
./gradlew "$@" :instrumented:integration:assembleRelease -PintegrationCompileSdk=35

metadata_log=$(mktemp)
trap 'rm -f "$metadata_log"' EXIT

if ./gradlew "$@" :sample:kotlin:checkUs1ReleaseAarMetadata -PsampleCompileSdk=35 > "$metadata_log" 2>&1; then
    echo "Expected Profiling to require compileSdk 36." >&2
    exit 1
fi

if ! grep -F -A 1 "Dependency ':features:dd-sdk-android-profiling' requires libraries" "$metadata_log" \
    | grep -F -B 1 'depend on it to compile against version 36 or later'; then
    cat "$metadata_log" >&2
    exit 1
fi

for compile_sdk in 36 37; do
    echo "Building the Kotlin sample with Profiling at compileSdk $compile_sdk"
    ./gradlew "$@" :sample:kotlin:assembleUs1Release -PsampleCompileSdk="$compile_sdk"
done
