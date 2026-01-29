#!/bin/bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Size Benchmark Build Script
# This script builds both flavors and compares APK sizes

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "========================================"
echo "  Size Benchmark - APK Size Comparison"
echo "========================================"
echo ""

cd "$PROJECT_ROOT"

# Build without Datadog flavor
echo "Building withoutDatadog release..."
./gradlew :sample:size-benchmark:assembleWithoutDatadogRelease --quiet

# Build with Datadog flavor
echo "Building withDatadog release..."
./gradlew :sample:size-benchmark:assembleWithDatadogRelease --quiet

echo ""
echo "Build completed!"
echo ""

# Find APK files
APK_WITHOUT_DATADOG="$SCRIPT_DIR/build/outputs/apk/withoutDatadog/release/size-benchmark-withoutDatadog-release-unsigned.apk"
APK_WITH_DATADOG="$SCRIPT_DIR/build/outputs/apk/withDatadog/release/size-benchmark-withDatadog-release-unsigned.apk"

# Check if APK files exist, try alternative naming
if [ ! -f "$APK_WITHOUT_DATADOG" ]; then
    APK_WITHOUT_DATADOG=$(find "$SCRIPT_DIR/build/outputs/apk/withoutDatadog/release" -name "*.apk" 2>/dev/null | head -1)
fi

if [ ! -f "$APK_WITH_DATADOG" ]; then
    APK_WITH_DATADOG=$(find "$SCRIPT_DIR/build/outputs/apk/withDatadog/release" -name "*.apk" 2>/dev/null | head -1)
fi

if [ -z "$APK_WITHOUT_DATADOG" ] || [ ! -f "$APK_WITHOUT_DATADOG" ]; then
    echo "Error: Could not find withoutDatadog APK"
    exit 1
fi

if [ -z "$APK_WITH_DATADOG" ] || [ ! -f "$APK_WITH_DATADOG" ]; then
    echo "Error: Could not find withDatadog APK"
    exit 1
fi

# Get file sizes in bytes
SIZE_WITHOUT_DATADOG=$(stat -f%z "$APK_WITHOUT_DATADOG" 2>/dev/null || stat -c%s "$APK_WITHOUT_DATADOG")
SIZE_WITH_DATADOG=$(stat -f%z "$APK_WITH_DATADOG" 2>/dev/null || stat -c%s "$APK_WITH_DATADOG")

# Calculate difference
SIZE_DIFF=$((SIZE_WITH_DATADOG - SIZE_WITHOUT_DATADOG))

# Convert to human readable format (KB and MB)
format_size() {
    local bytes=$1
    if [ $bytes -ge 1048576 ]; then
        echo "$(echo "scale=2; $bytes / 1048576" | bc) MB"
    else
        echo "$(echo "scale=2; $bytes / 1024" | bc) KB"
    fi
}

SIZE_WITHOUT_HR=$(format_size $SIZE_WITHOUT_DATADOG)
SIZE_WITH_HR=$(format_size $SIZE_WITH_DATADOG)
SIZE_DIFF_HR=$(format_size $SIZE_DIFF)

# Calculate percentage increase
if [ $SIZE_WITHOUT_DATADOG -gt 0 ]; then
    PERCENTAGE=$(echo "scale=2; ($SIZE_DIFF * 100) / $SIZE_WITHOUT_DATADOG" | bc)
else
    PERCENTAGE="N/A"
fi

# Print results
echo "========================================"
echo "  APK Size Comparison Results"
echo "========================================"
echo ""
echo "Without Datadog SDK:"
echo "  File: $(basename "$APK_WITHOUT_DATADOG")"
echo "  Size: $SIZE_WITHOUT_HR ($SIZE_WITHOUT_DATADOG bytes)"
echo ""
echo "With Datadog SDK:"
echo "  File: $(basename "$APK_WITH_DATADOG")"
echo "  Size: $SIZE_WITH_HR ($SIZE_WITH_DATADOG bytes)"
echo ""
echo "----------------------------------------"
echo "Size Difference: +$SIZE_DIFF_HR (+$SIZE_DIFF bytes)"
echo "Percentage Increase: +$PERCENTAGE%"
echo "========================================"
echo ""
echo "APK Locations:"
echo "  Without Datadog: $APK_WITHOUT_DATADOG"
echo "  With Datadog:    $APK_WITH_DATADOG"
