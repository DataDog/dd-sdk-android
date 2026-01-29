#!/bin/bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Size Benchmark Build Script
# This script builds all Datadog modules individually and compares APK sizes

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# All Datadog modules to test
DATADOG_MODULES=(
    # Feature modules
    ":features:dd-sdk-android-logs"
    ":features:dd-sdk-android-flags"
    ":features:dd-sdk-android-flags-openfeature"
    ":features:dd-sdk-android-rum"
    ":features:dd-sdk-android-rum-debug-widget"
    ":features:dd-sdk-android-trace"
    ":features:dd-sdk-android-trace-otel"
    ":features:dd-sdk-android-ndk"
    ":features:dd-sdk-android-webview"
    ":features:dd-sdk-android-session-replay"
    ":features:dd-sdk-android-session-replay-material"
    ":features:dd-sdk-android-session-replay-compose"
    ":features:dd-sdk-android-profiling"
    # Integration modules
    ":integrations:dd-sdk-android-trace-coroutines"
    ":integrations:dd-sdk-android-rum-coroutines"
    ":integrations:dd-sdk-android-rx"
    ":integrations:dd-sdk-android-timber"
    ":integrations:dd-sdk-android-coil"
    ":integrations:dd-sdk-android-coil3"
    ":integrations:dd-sdk-android-glide"
    ":integrations:dd-sdk-android-fresco"
    ":integrations:dd-sdk-android-sqldelight"
    ":integrations:dd-sdk-android-compose"
    ":integrations:dd-sdk-android-cronet"
    ":integrations:dd-sdk-android-okhttp"
    ":integrations:dd-sdk-android-okhttp-otel"
    # Tools
    ":tools:benchmark"
    ":zstd"
)

echo "========================================"
echo "  Size Benchmark - Per-Module Analysis"
echo "========================================"
echo ""

cd "$PROJECT_ROOT"

# Build baseline (without Datadog)
echo "Building baseline (withoutDatadog)..."
./gradlew :sample:size-benchmark:assembleWithoutDatadogRelease --quiet

# Find baseline APK
BASELINE_APK=$(find "$SCRIPT_DIR/build/outputs/apk/withoutDatadog/release" -name "*.apk" 2>/dev/null | head -1)

if [ -z "$BASELINE_APK" ] || [ ! -f "$BASELINE_APK" ]; then
    echo "Error: Could not find baseline APK"
    exit 1
fi

# Get baseline size
BASELINE_SIZE=$(stat -f%z "$BASELINE_APK" 2>/dev/null || stat -c%s "$BASELINE_APK")

# Convert bytes to human readable format
format_size() {
    local bytes=$1
    if [ $bytes -ge 1048576 ]; then
        echo "$(echo "scale=2; $bytes / 1048576" | bc) MB"
    else
        echo "$(echo "scale=2; $bytes / 1024" | bc) KB"
    fi
}

BASELINE_SIZE_HR=$(format_size $BASELINE_SIZE)

echo ""
echo "Baseline APK size (no Datadog): $BASELINE_SIZE_HR ($BASELINE_SIZE bytes)"
echo ""
echo "========================================"
echo "  Building each module individually..."
echo "========================================"
echo ""

# Arrays to store results
declare -a MODULE_NAMES
declare -a MODULE_SIZES
declare -a MODULE_DIFFS

# Build each module
for module in "${DATADOG_MODULES[@]}"; do
    # Extract short name for display
    short_name=$(echo "$module" | sed 's/.*://')
    
    echo -n "Building with $short_name... "
    
    # Clean and build with this specific module
    ./gradlew :sample:size-benchmark:assembleWithDatadogRelease -PdatadogModule="$module" --quiet 2>/dev/null || {
        echo "FAILED (skipping)"
        continue
    }
    
    # Find the APK
    MODULE_APK=$(find "$SCRIPT_DIR/build/outputs/apk/withDatadog/release" -name "*.apk" 2>/dev/null | head -1)
    
    if [ -z "$MODULE_APK" ] || [ ! -f "$MODULE_APK" ]; then
        echo "APK not found (skipping)"
        continue
    fi
    
    # Get size
    MODULE_SIZE=$(stat -f%z "$MODULE_APK" 2>/dev/null || stat -c%s "$MODULE_APK")
    SIZE_DIFF=$((MODULE_SIZE - BASELINE_SIZE))
    
    # Store results
    MODULE_NAMES+=("$short_name")
    MODULE_SIZES+=("$MODULE_SIZE")
    MODULE_DIFFS+=("$SIZE_DIFF")
    
    SIZE_DIFF_HR=$(format_size $SIZE_DIFF)
    echo "done (+$SIZE_DIFF_HR)"
done

echo ""
echo "========================================"
echo "  Size Impact Summary"
echo "========================================"
echo ""
printf "%-45s %15s %15s %10s\n" "Module" "APK Size" "Size Increase" "% Increase"
printf "%-45s %15s %15s %10s\n" "------" "--------" "-------------" "----------"
printf "%-45s %15s %15s %10s\n" "(baseline - no Datadog)" "$BASELINE_SIZE_HR" "-" "-"
echo ""

# Sort results by size difference (descending)
# Create indexed array for sorting
indices=($(for i in "${!MODULE_DIFFS[@]}"; do echo "$i ${MODULE_DIFFS[$i]}"; done | sort -k2 -rn | cut -d' ' -f1))

for i in "${indices[@]}"; do
    name="${MODULE_NAMES[$i]}"
    size="${MODULE_SIZES[$i]}"
    diff="${MODULE_DIFFS[$i]}"
    
    size_hr=$(format_size $size)
    diff_hr=$(format_size $diff)
    
    if [ $BASELINE_SIZE -gt 0 ]; then
        percentage=$(echo "scale=2; ($diff * 100) / $BASELINE_SIZE" | bc)
    else
        percentage="N/A"
    fi
    
    printf "%-45s %15s %15s %9s%%\n" "$name" "$size_hr" "+$diff_hr" "+$percentage"
done

echo ""
echo "========================================"
echo "  Build Complete"
echo "========================================"
