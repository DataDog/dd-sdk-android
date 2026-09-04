/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

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

    // Tests
    id("de.mobilej.unmock")
}

android {
    namespace = "com.datadog.android.core.stub"
}

dependencies {
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)

    // Testing — mockito-kotlin/JUnit are used from this module's MAIN source set (it is a test
    // stub helper), so these belong on `implementation`, not the test classpath.
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
    implementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    implementation(libs.okHttp)
    implementation(libs.gson)
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig()
}
