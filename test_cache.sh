#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

java --version

CACHE_TEST_HOME="/Users/aleksandr.gringauz/projects/test-cache-1/"

GRADLE_USER_HOME="$CACHE_TEST_HOME" ./gradlew \
  --console=plain \
  --continue \
  --scan \
  -Dscan.uploadInBackground=false \
  -PverificationBuildType=debug \
  checkGeneratedFiles \
  ktlintCheck \
  :build-logic:ktlintCheck \
  detekt \
  lintDebug \
  :dd-sdk-android-core:testDebugUnitTest \
  :dd-sdk-android-internal:testDebugUnitTest \
  :unitTestDebugFeatures \
  :unitTestDebugIntegrations \
  :sample:benchmark:testDebugUnitTest \
  :tools:unit:testJvmDebugUnitTest \
  :tools:detekt:test \
  :tools:lint:test \
  :tools:noopfactory:test \
  :tools:benchmark:testDebugUnitTest \
  :build-logic:test \
  :dd-sdk-android-core:koverXmlReportDebug \
  :dd-sdk-android-internal:koverXmlReportDebug \
  :koverReportDebugFeatures \
  :koverReportDebugIntegrations \
  :reliability:core-it:assembleDebug \
  :reliability:core-it:assembleDebugAndroidTest

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
