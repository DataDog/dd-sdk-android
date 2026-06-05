#!/usr/bin/env bash

# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.

# Post benchmark comparison results as a PR comment via the internal pr-commenter service.
# Skips silently on develop (no PR to comment on) or if the comparison file is empty.
# Follows the same pattern as dd-trace-py/.gitlab/scripts/post-pr-comment.sh.

set -euo pipefail

MESSAGE_FILE="benchmark-comparison.md"
HEADER="Android Benchmark Results"

# Skip on develop — no PR to comment on
if [ "${CI_COMMIT_REF_NAME}" = "develop" ]; then
  echo "Skipping PR comment on develop branch."
  exit 0
fi

# Bail out if there is nothing to say
[ -s "$MESSAGE_FILE" ] || { echo "No comparison report found, skipping PR comment."; exit 0; }

MESSAGE="$(awk '{printf "%s\\n", $0}' "$MESSAGE_FILE" | sed 's/\"/\\"/g')"

AUTHANYWHERE_DIR="$(mktemp -d)"
trap 'rm -rf "$AUTHANYWHERE_DIR"' EXIT
wget -nv -P "$AUTHANYWHERE_DIR" binaries.ddbuild.io/dd-source/authanywhere/LATEST/authanywhere-linux-amd64
chmod +x "$AUTHANYWHERE_DIR/authanywhere-linux-amd64"

curl 'https://pr-commenter.us1.ddbuild.io/internal/cit/pr-comment' \
  -H "$("$AUTHANYWHERE_DIR/authanywhere-linux-amd64")" \
  -X PATCH -d "{
    \"commit\": \"$CI_COMMIT_SHORT_SHA\",
    \"message\": \"$MESSAGE\",
    \"header\": \"$HEADER\",
    \"org\": \"Datadog\",
    \"repo\": \"dd-sdk-android\"
  }"
