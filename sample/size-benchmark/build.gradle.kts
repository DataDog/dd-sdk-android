/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java17

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        applicationId = "com.datadog.android.sample.sizebenchmark"
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name

        buildFeatures {
            buildConfig = true
        }
    }

    namespace = "com.datadog.android.sample.sizebenchmark"

    compileOptions {
        java17()
    }

    flavorDimensions += listOf("datadog")
    productFlavors {
        register("withDatadog") {
            isDefault = true
            dimension = "datadog"
        }
        register("withoutDatadog") {
            dimension = "datadog"
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }

        getByName("release") {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
}

dependencies {
    // Kotlin
    implementation(libs.kotlin)

    // Kotlin Coroutines
    implementation(libs.coroutinesCore)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")

    // Android dependencies
    implementation(libs.androidXAppCompat)
    implementation(libs.googleMaterial)
    implementation(libs.androidXConstraintLayout)

    // OkHttp
    implementation(libs.okHttp)

    // WorkManager
    implementation(libs.androidXWorkManager)

    // Datadog Libraries - only for withDatadog flavor
    // Feature modules
    "withDatadogImplementation"(project(":features:dd-sdk-android-logs"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-flags"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-flags-openfeature"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-rum"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-rum-debug-widget"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-trace"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-trace-otel"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-ndk"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-webview"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-session-replay"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-session-replay-material"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-session-replay-compose"))
    "withDatadogImplementation"(project(":features:dd-sdk-android-profiling"))

    // Integration modules
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-trace-coroutines"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-rum-coroutines"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-rx"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-timber"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-coil"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-coil3"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-glide"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-fresco"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-sqldelight"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-compose"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-cronet"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-okhttp"))
    "withDatadogImplementation"(project(":integrations:dd-sdk-android-okhttp-otel"))
}
