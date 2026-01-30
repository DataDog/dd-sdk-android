/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.kotlinConfig

plugins {
    id("com.android.library")
    kotlin("android")
    alias(libs.plugins.composeCompilerPlugin)
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("kotlin-parcelize")
}

android {
    namespace = "com.datadog.sample.benchmark"
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
    }

    compileOptions {
        java17()
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.adapterDelegatesViewBinding)
    implementation(libs.androidXMultidex)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXConstraintLayout)
    implementation(libs.androidXLifecycleCompose)
    implementation(libs.googleMaterial)
    implementation(libs.bundles.glide)
    implementation(libs.timber)
    implementation(platform(libs.androidXComposeBom))
    implementation(libs.material3Android)
    implementation(libs.bundles.androidXCompose)
    implementation(libs.coilCompose)
    implementation(libs.daggerLib)
    kapt(libs.daggerCompiler)
    kapt(libs.glideCompiler)
    implementation(libs.coroutinesCore)
    implementation(libs.bundles.ktorClient)
    implementation(libs.kotlinxSerializationJson)
    // OpenTelemetry for tracer interface
    implementation(libs.openTelemetryApi)

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.ktorClientMock)
}

kotlinConfig()

