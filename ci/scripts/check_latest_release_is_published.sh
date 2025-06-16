#!/usr/bin/env bash
#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

set -o pipefail

tag_name=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
     -H "Accept: application/vnd.github+json" \
     https://api.github.com/repos/DataDog/dd-sdk-android/releases/latest | jq -r .tag_name)

repos_list=(
  "dd-sdk-android-core"
  "dd-sdk-android-internal"
  "dd-sdk-android-rum"
  "dd-sdk-android-logs"
  "dd-sdk-android-trace"
  "dd-sdk-android-trace-otel"
  "dd-sdk-android-webview"
  "dd-sdk-android-session-replay"
  "dd-sdk-android-session-replay-compose"
  "dd-sdk-android-session-replay-material"
  "dd-sdk-android-ndk"
  "dd-sdk-android-coil"
  "dd-sdk-android-compose"
  "dd-sdk-android-glide"
  "dd-sdk-android-okhttp"
  "dd-sdk-android-okhttp-otel"
  "dd-sdk-android-rum-coroutines"
  "dd-sdk-android-rx"
  "dd-sdk-android-sqldelight"
  "dd-sdk-android-timber"
  "dd-sdk-android-trace-coroutines"
  "dd-sdk-android-tv"
)

for repo in "${repos_list[@]}"; do
  status_code=$(curl -s -o /dev/null -w "%{http_code}" "https://repo1.maven.org/maven2/com/datadoghq/$repo/$tag_name/$repo-$tag_name.aar")

  if [ $status_code -eq 200 ]; then
    echo "Release $tag_name exists for $repo"
  else
    echo "Release $tag_name doesn't exist for $repo"
    exit 1
  fi
done
