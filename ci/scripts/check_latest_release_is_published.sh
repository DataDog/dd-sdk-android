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

if [ -z "$tag_name" ] || [ "$tag_name" = "null" ]; then
  echo "Error: Failed to retrieve tag_name from GitHub API: tag_name='$tag_name'"
  exit 1
fi

for artifactId in $(./gradlew -q listAllPublishedArtifactIds); do
  artifact_url="https://repo1.maven.org/maven2/com/datadoghq/$artifactId/$tag_name/$artifactId-$tag_name.aar"
  status_code=$(curl -s -o /dev/null -w "%{http_code}" "$artifact_url")

  if [ $status_code -eq 200 ]; then
    echo "Release $tag_name exists for $artifactId"
    exit 0
  elif [ $status_code -eq 404 ]; then
    echo "Release $tag_name doesn't exist for $artifactId"
    echo "URL: $artifact_url"
    exit 1
  else
    echo "Error: Unexpected status code $status_code when checking for $artifactId"
    echo "URL: $artifact_url"
    exit 1
  fi
done
