#!/bin/bash

# Script to compile and compare sizes of noDatadog and withDatadog benchmark apps
# Uses APK splits, compares only arm64-v8a architecture
# Usage: ./compare_sizes.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ABI="arm64-v8a"

echo "========================================"
echo "Building Benchmark Apps (Release)"
echo "Comparing architecture: $ABI"
echo "========================================"
echo ""

cd "$ROOT_DIR"

# Build both apps
echo "Building noDatadog release APKs..."
./gradlew :sample:benchmark:noDatadog:assembleRelease --quiet

echo "Building withDatadog release APKs..."
./gradlew :sample:benchmark:withDatadog:assembleRelease --quiet

echo ""
echo "=================================="
echo "APK Size Comparison ($ABI)"
echo "=================================="
echo ""

# Find the arm64-v8a APK files
NO_DATADOG_APK=$(find "$SCRIPT_DIR/noDatadog/build/outputs/apk/release" -name "*${ABI}*.apk" 2>/dev/null | head -1)
WITH_DATADOG_APK=$(find "$SCRIPT_DIR/withDatadog/build/outputs/apk/release" -name "*${ABI}*.apk" 2>/dev/null | head -1)

# Verify APKs exist
if [ -z "$NO_DATADOG_APK" ] || [ ! -f "$NO_DATADOG_APK" ]; then
    echo "Error: noDatadog $ABI APK not found"
    echo "Available APKs:"
    ls -la "$SCRIPT_DIR/noDatadog/build/outputs/apk/release/" 2>/dev/null || echo "  (none)"
    exit 1
fi

if [ -z "$WITH_DATADOG_APK" ] || [ ! -f "$WITH_DATADOG_APK" ]; then
    echo "Error: withDatadog $ABI APK not found"
    echo "Available APKs:"
    ls -la "$SCRIPT_DIR/withDatadog/build/outputs/apk/release/" 2>/dev/null || echo "  (none)"
    exit 1
fi

# Get sizes in bytes
NO_DATADOG_SIZE=$(stat -f%z "$NO_DATADOG_APK" 2>/dev/null || stat -c%s "$NO_DATADOG_APK")
WITH_DATADOG_SIZE=$(stat -f%z "$WITH_DATADOG_APK" 2>/dev/null || stat -c%s "$WITH_DATADOG_APK")

# Calculate difference
SIZE_DIFF=$((WITH_DATADOG_SIZE - NO_DATADOG_SIZE))

# Convert to human-readable format
format_size() {
    local size=$1
    if [ $size -ge 1048576 ]; then
        echo "$(echo "scale=2; $size / 1048576" | bc) MB"
    elif [ $size -ge 1024 ]; then
        echo "$(echo "scale=2; $size / 1024" | bc) KB"
    else
        echo "$size bytes"
    fi
}

NO_DATADOG_HR=$(format_size $NO_DATADOG_SIZE)
WITH_DATADOG_HR=$(format_size $WITH_DATADOG_SIZE)
SIZE_DIFF_HR=$(format_size $SIZE_DIFF)

# Calculate percentage increase
if [ $NO_DATADOG_SIZE -gt 0 ]; then
    PERCENT_INCREASE=$(echo "scale=2; ($SIZE_DIFF * 100) / $NO_DATADOG_SIZE" | bc)
else
    PERCENT_INCREASE="N/A"
fi

# Print results
printf "%-20s %s\n" "noDatadog APK:" "$NO_DATADOG_HR ($NO_DATADOG_SIZE bytes)"
printf "%-20s %s\n" "withDatadog APK:" "$WITH_DATADOG_HR ($WITH_DATADOG_SIZE bytes)"
echo ""
echo "----------------------------------"
printf "%-20s %s\n" "Size difference:" "+$SIZE_DIFF_HR (+$SIZE_DIFF bytes)"
printf "%-20s %s%%\n" "Percentage increase:" "$PERCENT_INCREASE"
echo ""
echo "APK locations:"
echo "  noDatadog:   $NO_DATADOG_APK"
echo "  withDatadog: $WITH_DATADOG_APK"

# List all generated APKs
echo ""
echo "All generated APKs:"
echo "  noDatadog:"
ls -lh "$SCRIPT_DIR/noDatadog/build/outputs/apk/release/"*.apk 2>/dev/null | awk '{print "    " $9 " (" $5 ")"}'
echo "  withDatadog:"
ls -lh "$SCRIPT_DIR/withDatadog/build/outputs/apk/release/"*.apk 2>/dev/null | awk '{print "    " $9 " (" $5 ")"}'
