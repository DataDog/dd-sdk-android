/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

// CORE LIBRARY
include(":dd-sdk-android-core")

// MAIN FEATURE LIBRARIES
include(":features:dd-sdk-android-rum")
include(":features:dd-sdk-android-logs")
include(":features:dd-sdk-android-ndk")
include(":features:dd-sdk-android-trace")
include(":features:dd-sdk-android-webview")
include(":features:dd-sdk-android-session-replay")
include(":features:dd-sdk-android-session-replay-compose")
include(":features:dd-sdk-android-session-replay-material")

// INTEGRATION LIBRARIES
include(":integrations:dd-sdk-android-coil")
include(":integrations:dd-sdk-android-compose")
include(":integrations:dd-sdk-android-fresco")
include(":integrations:dd-sdk-android-glide")
include(":integrations:dd-sdk-android-rx")
include(":integrations:dd-sdk-android-sqldelight")
include(":integrations:dd-sdk-android-timber")
include(":integrations:dd-sdk-android-tv")
include(":integrations:dd-sdk-android-okhttp")
include(":integrations:dd-sdk-android-rum-coroutines")
include(":integrations:dd-sdk-android-trace-coroutines")

// TESTING UTILS
include(":reliability:stub-core")

// SINGLE FEATURE INTEGRATION TESTS
include(":reliability:single-fit:logs")
include(":reliability:single-fit:rum")
include(":reliability:single-fit:trace")

// LEGACY TESTS
include(":instrumented:integration")

// SAMPLE PROJECTS
include(":sample:kotlin")
include(":sample:wear")
include(":sample:vendor-lib")

// TOOLCHAIN
include(":tools:detekt")
include(":tools:unit")
include(":tools:noopfactory")
include(":tools:javabackport")
include(":tools:lint")
