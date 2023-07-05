/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

include(":dd-sdk-android-core")
include(":integrations:dd-sdk-android-coil")
include(":integrations:dd-sdk-android-compose")
include(":integrations:dd-sdk-android-fresco")
include(":integrations:dd-sdk-android-glide")
include(":integrations:dd-sdk-android-ktx")
include(":integrations:dd-sdk-android-rx")
include(":integrations:dd-sdk-android-sqldelight")
include(":integrations:dd-sdk-android-timber")
include(":integrations:dd-sdk-android-tv")
include(":integrations:dd-sdk-android-okhttp")

include(":instrumented:integration")
include(":instrumented:nightly-tests")

include(":features:dd-sdk-android-rum")
include(":features:dd-sdk-android-logs")
include(":features:dd-sdk-android-ndk")
include(":features:dd-sdk-android-trace")
include(":features:dd-sdk-android-webview")
include(":features:dd-sdk-android-session-replay")
include(":features:dd-sdk-android-session-replay-material")

include(":sample:kotlin")
include(":sample:wear")

include(":tools:detekt")
include(":tools:unit")
include(":tools:noopfactory")
include(":tools:javabackport")
include(":tools:lint")
