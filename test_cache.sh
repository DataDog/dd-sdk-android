#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

CACHE_TEST_HOME="/Users/aleksandr.gringauz/projects/test-cache-1/"

GRADLE_USER_HOME="$CACHE_TEST_HOME" ./gradlew \
  --console=plain \
  --continue \
  --scan \
  -Dscan.uploadInBackground=false \
  -PverificationBuildType=release \
  checkGeneratedFiles \
  ktlintCheck \
  :build-logic:ktlintCheck \
  detekt \
  lintRelease \
  :dd-sdk-android-core:testReleaseUnitTest \
  :dd-sdk-android-internal:testReleaseUnitTest \
  :unitTestReleaseFeatures \
  :unitTestReleaseIntegrations \
  :sample:benchmark:testReleaseUnitTest \
  :tools:unit:testJvmReleaseUnitTest \
  :tools:detekt:test \
  :tools:lint:test \
  :tools:noopfactory:test \
  :tools:benchmark:testReleaseUnitTest \
  :build-logic:test \
  :dd-sdk-android-core:koverXmlReportRelease \
  :dd-sdk-android-internal:koverXmlReportRelease \
  :koverReportFeatures \
  :koverReportIntegrations

#GRADLE_USER_HOME="$CACHE_TEST_HOME" ./gradlew \
#  --build-cache \
#  --info \
#  :features:dd-sdk-android-rum-internal:clean \
#  :features:dd-sdk-android-rum-internal:compileDebugUnitTestKotlin \
#  | tee /tmp/cache-run-1.log
#
#GRADLE_USER_HOME="$CACHE_TEST_HOME" ./gradlew \
#  --build-cache \
#  --info \
#  :features:dd-sdk-android-rum-internal:clean \
#  :features:dd-sdk-android-rum-internal:compileDebugUnitTestKotlin \
#  | tee /tmp/cache-run-2.log