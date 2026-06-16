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
# Usage: ./ci/scripts/reset-dogfooding.sh [--force]
#   --force  Skip the in-flight features confirmation (use in CI/non-interactive environments).
# Run from the repo root on any branch.
# NOTE: After this script completes your active branch will be `dogfooding`.

set -euo pipefail

trap 'rm -f "${ANDROID_CONFIG}.tmp"' EXIT

DOGFOODING_BRANCH="dogfooding"
ANDROID_CONFIG="buildSrc/src/main/kotlin/com/datadog/gradle/config/AndroidConfig.kt"
FORCE=false

for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

echo "Fetching latest from origin..."
git fetch origin

# Check for in-flight features that will be discarded
AHEAD=$(git log origin/develop..origin/"$DOGFOODING_BRANCH" --oneline 2>/dev/null || true)
if [ -n "$AHEAD" ] && [ "$FORCE" = false ]; then
  echo "⚠️  The following commits on $DOGFOODING_BRANCH will be discarded:"
  echo "$AHEAD"
  echo ""
  read -p "Are you sure you want to reset? [y/N] " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    echo "Aborted."
    exit 0
  fi
fi

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
