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

for artifactId in $(./gradlew -q listAllPublishedArtifactIds); do
  status_code=$(curl -s -o /dev/null -w "%{http_code}" "https://repo1.maven.org/maven2/com/datadoghq/$artifactId/$tag_name/$artifactId-$tag_name.aar")

  if [ $status_code -eq 200 ]; then
    echo "Release $tag_name exists for $artifactId"
  else
    echo "Release $tag_name doesn't exist for $artifactId"
    # TODO remove this check when
    # https://github.com/DataDog/dd-sdk-android/commit/ccd79322895a6ba135e2b73b32005fb4aeb5c31c
    # is released
    if [ $artifactId != "dd-sdk-android-benchmark-internal" ]; then
      exit 1
    fi
  fi
done
