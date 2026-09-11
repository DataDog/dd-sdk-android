/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForTvApp
import com.datadog.gradle.config.depotProxied
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Applied before the Android plugin on purpose (not under "Analysis tools"): AGP 9 applies
    // the Kotlin plugin itself, and ktlint-gradle 14.2.0 registers its Android source-set tasks
    // twice when it comes after the Kotlin plugin.
    id("ktlint")

    // Build
    id("com.android.application")
    id("datadogBuildConfig")
    alias(libs.plugins.datadogGradlePlugin)
}

android {
    namespace = "com.datadog.android.tv.sample"
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name

        vectorDrawables.useSupportLibrary = true

        configureFlavorForTvApp(project.rootDir)
    }

    compileOptions {
        java17()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

repositories.depotProxied(providers) {}

dependencies {

    implementation(project(":dd-sdk-android-core"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":features:dd-sdk-android-session-replay-material"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
    implementation(project(":integrations:dd-sdk-android-timber"))
    implementation(project(":integrations:dd-sdk-android-tv"))

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.androidXCore)
    implementation(libs.androidXCoreKtx)
    implementation(libs.androidXAppCompat)
    implementation(libs.googleMaterial)
    implementation(libs.androidXRecyclerView)
    implementation(libs.androidXConstraintLayout)
    implementation(libs.androidXLifecycleLiveDataKtx)
    implementation(libs.androidXLifecycleViewModelKtx)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXNavigationUIKtx)

    // Network
    implementation(libs.okHttp)
    implementation(libs.gson)

    // Misc
    implementation(libs.timber)

    // Video
    implementation(libs.bundles.media3)
    implementation(libs.newPipeExtractor)
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
