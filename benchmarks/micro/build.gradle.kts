/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig

plugins {
    id("com.android.library")
    kotlin("android")
    alias(libs.plugins.androidXBenchmarkPlugin)
}

android {
    namespace = "com.datadog.benchmark.microbenchmark"
    compileSdk = AndroidConfig.TARGET_SDK

    defaultConfig {
        minSdk = 24
        targetSdk = AndroidConfig.TARGET_SDK
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    testBuildType = "release"
    buildTypes {
        release {
            isDefault = true
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidXBenchmarkJunit4)
    androidTestImplementation(libs.androidXTestRunner)
    androidTestImplementation(libs.androidXTestJUnitExt)
    androidTestImplementation(libs.jUnit4)
}
