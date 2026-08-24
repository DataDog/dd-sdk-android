/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    id("datadogBuildConfig")

    // Analysis tools
    id("test-pyramid-api-usage")

    // Tests
    id("de.mobilej.unmock")
}

android {
    namespace = "com.datadog.android.logs.integration"
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(libs.kotlin)

    // Testing
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(project(":reliability:stub-core"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.okHttp)
    testImplementation(libs.gson)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("android.os")
    keepStartingWith("org.json")
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyJunitConfig()
}
