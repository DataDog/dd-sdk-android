/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForAutoApp
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Build
    id("com.android.application")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    id("datadogBuildConfig")
    alias(libs.plugins.datadogGradlePlugin)
}

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
android {
    compileSdk = AndroidConfig.COMPILE_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK_FOR_AUTO
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name

        buildFeatures {
            buildConfig = true
        }

        configureFlavorForAutoApp(project.rootDir)
    }

    namespace = "com.datadog.sample.automotive"

    compileOptions {
        java17()
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Datadog Libraries
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-rum"))

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.androidXCoreKtx)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXLegacySupportV4)
    implementation(libs.androidXLegacySupportV13)

    // Android Car
    implementation(libs.androidXCarApp)
    implementation(libs.androidXCarAutomotive)
}

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
}

taskConfig<KotlinCompile> {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
    }
}
