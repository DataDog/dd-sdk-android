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

// Get the Datadog module to include from gradle property
// Usage: ./gradlew :sample:size-benchmark:assembleWithDatadogRelease -PdatadogModule=:features:dd-sdk-android-rum
val datadogModule: String? = project.findProperty("datadogModule") as String?

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

    // Datadog module - only for withDatadog flavor
    // The module is specified via -PdatadogModule=:path:to:module gradle property
    // If no module is specified, no Datadog dependency is added
    if (datadogModule != null) {
        logger.lifecycle("Size Benchmark: Including Datadog module '$datadogModule'")
        "withDatadogImplementation"(project(datadogModule))
    } else {
        logger.lifecycle("Size Benchmark: No Datadog module specified (-PdatadogModule not set)")
    }
}
