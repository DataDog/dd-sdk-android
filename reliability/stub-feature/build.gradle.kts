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
}

android {
    namespace = "com.datadog.android.api.feature.stub"
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)

    // Testing
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
}

datadogBuild {
    applyAndroidLibraryConfig()
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
}
