#
# Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
# This product includes software developed at Datadog (https://www.datadoghq.com/).
# Copyright 2016-Present Datadog, Inc.
#

CACHE_TEST_HOME="/private/var/folders/s8/kbx30lvx3ms0gm63bxp2yd1h0000gp/T/tmp.6N6ETBEfEu"

GRADLE_USER_HOME="$CACHE_TEST_HOME" ./gradlew \
  --build-cache \
  --info \
  :features:dd-sdk-android-rum-internal:clean \
  :features:dd-sdk-android-rum-internal:compileDebugUnitTestKotlin

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