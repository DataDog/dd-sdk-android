/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

enableFeaturePreview("VERSION_CATALOGS")

include(":dd-sdk-android")
include(":dd-sdk-android-coil")
include(":dd-sdk-android-compose")
include(":dd-sdk-android-fresco")
include(":dd-sdk-android-glide")
include(":dd-sdk-android-ktx")
include(":dd-sdk-android-ndk")
include(":dd-sdk-android-rx")
include(":dd-sdk-android-sqldelight")
include(":dd-sdk-android-timber")
include(":dd-sdk-android-tv")

include(":instrumented:integration")
include(":instrumented:nightly-tests")

include(":library:dd-sdk-android-session-replay")

include(":sample:kotlin")

include(":tools:detekt")
include(":tools:unit")
include(":tools:noopfactory")
include(":tools:javabackport")
