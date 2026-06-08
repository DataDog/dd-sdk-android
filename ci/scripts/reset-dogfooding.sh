#!/usr/bin/env bash
#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#
# reset-dogfooding.sh
#
# Resets the `dogfooding` branch to the current HEAD of `develop`,
# applies the dogfooding version label, commits, and force-pushes.
#
# Usage: ./ci/scripts/reset-dogfooding.sh
# Run from the repo root on any branch.
# NOTE: After this script completes your active branch will be `dogfooding`.

set -euo pipefail

trap 'rm -f "${ANDROID_CONFIG}.tmp"' EXIT

DOGFOODING_BRANCH="dogfooding"
ANDROID_CONFIG="buildSrc/src/main/kotlin/com/datadog/gradle/config/AndroidConfig.kt"

echo "Fetching latest from origin..."
git fetch origin

echo "Resetting $DOGFOODING_BRANCH to origin/develop..."
git checkout "$DOGFOODING_BRANCH" 2>/dev/null || git checkout -b "$DOGFOODING_BRANCH" "origin/develop"
git reset --hard "origin/develop"

echo "Patching version in $ANDROID_CONFIG..."
# Replace: Version.Type.Snapshot() (no label, as develop uses)
# With:    Version.Type.Snapshot("dogfooding")
# GNU sed (Linux/CI) uses -i without argument; BSD sed (macOS) requires -i ''
# Use a temp-file approach that works on both:
sed 's/Version\.Type\.Snapshot()/Version.Type.Snapshot("dogfooding")/g' \
    "$ANDROID_CONFIG" > "${ANDROID_CONFIG}.tmp" \
    && mv "${ANDROID_CONFIG}.tmp" "$ANDROID_CONFIG"

# Verify the patch was applied
if ! grep -q 'Version\.Type\.Snapshot("dogfooding")' "$ANDROID_CONFIG"; then
  echo "ERROR: failed to patch $ANDROID_CONFIG — expected pattern not found." >&2
  exit 1
fi

echo "Committing version patch..."
git add "$ANDROID_CONFIG"
git commit -m "Reset dogfooding branch to develop"

echo "Force-pushing to origin/$DOGFOODING_BRANCH..."
git push --force-with-lease origin "$DOGFOODING_BRANCH"

echo ""
echo "Done. dogfooding branch is now at:"
git log --oneline -1
