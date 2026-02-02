/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java11

plugins {
    id("com.android.library")
    kotlin("android")
    id("androidx.benchmark")
}

android {
    namespace = "com.datadog.benchmark.compression"

    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"

        // Suppress emulator error for testing on emulators (remove for real device benchmarks)
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        java11()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Run benchmarks in release mode for accurate results
    testBuildType = "release"

    sourceSets {
        named("androidTest") {
            java.srcDir("src/androidTest/kotlin")
            assets.srcDir("src/androidTest/assets")
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/jvm.kotlin_module",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }
}

dependencies {
    // Compression libraries to benchmark
    androidTestImplementation(project(":zstd"))
    androidTestImplementation(project(":zstd-java"))

    // Benchmark library
    androidTestImplementation(libs.androidXBenchmarkJunit4)

    // OkIO for Gzip compression
    androidTestImplementation(libs.okHttp)

    // Test infrastructure
    androidTestImplementation(libs.bundles.integrationTests)
    androidTestImplementation(libs.assertJ)
}
