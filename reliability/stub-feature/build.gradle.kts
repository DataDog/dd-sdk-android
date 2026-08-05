/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.android.build.api.attributes.ProductFlavorAttr

plugins {
    // Applied before the Android plugin on purpose (not under "Analysis tools"): AGP 9 applies
    // the Kotlin plugin itself, and ktlint-gradle 14.2.0 registers its Android source-set tasks
    // twice when it comes after the Kotlin plugin.
    id("ktlint")

    // Build
    id("com.android.library")
    id("datadogBuildConfig")

    // Analysis tools
    id("test-pyramid-api-usage")
}

android {
    namespace = "com.datadog.android.api.feature.stub"
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)

    // Testing — mockito-kotlin/JUnit are used from this module's MAIN source set (it is a test
    // stub helper), so these belong on `implementation`, not the test classpath.
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
    implementation(project(":tools:unit")) {
        attributes {
            attribute(
                ProductFlavorAttr.of("platform"),
                // This module is consumed only by :reliability:core-it's androidTest (instrumented)
                // classpath, which requests the "art" variant of :tools:unit. Requesting "jvm" here
                // would pull a second variant of the same project and cause a capability conflict.
                objects.named("art")
            )
        }
    }
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig()
}
