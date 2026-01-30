/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForBenchmark
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig

plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.composeCompilerPlugin)
    kotlin("kapt")
    kotlin("plugin.serialization")
    id("kotlin-parcelize")
    // NO Datadog Gradle Plugin here!
    id("transitiveDependencies")
}

android {
    namespace = "com.datadog.sample.benchmark"
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        applicationId = "com.datadog.sample.benchmark.nodatadog"
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        multiDexEnabled = true

        buildFeatures {
            buildConfig = true
        }
        vectorDrawables.useSupportLibrary = true
        configureFlavorForBenchmark(project.rootDir)
    }

    compileOptions {
        java17()
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    val bmPassword = System.getenv("BM_STORE_PASSWD")
    signingConfigs {
        if (bmPassword != null) {
            create("release") {
                storeFile = File(project.rootDir, "sample-benchmark.keystore")
                storePassword = bmPassword
                keyAlias = "dd-sdk-android"
                keyPassword = bmPassword
            }
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
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            } ?: kotlin.run {
                signingConfig = signingConfigs.findByName("debug")
            }
        }
    }
}

dependencies {
    implementation(project(":sample:benchmark:common"))

    // NO Datadog SDK dependencies!

    // Dependencies needed for Dagger annotation processing (kapt needs to resolve all types)
    implementation(libs.okHttp)
    implementation(libs.bundles.ktorClient)
    implementation(libs.openTelemetryApi)
    implementation(libs.androidXAppCompat)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.bundles.glide)
    implementation(libs.coroutinesCore)
    
    // Compose dependencies
    implementation(platform(libs.androidXComposeBom))
    implementation(libs.bundles.androidXCompose)

    // Dagger for this module
    implementation(libs.daggerLib)
    kapt(libs.daggerCompiler)
    kapt(libs.glideCompiler)

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.systemStubsJupiter)
    testImplementation(libs.ktorClientMock)
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
