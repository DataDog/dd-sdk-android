/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.sampleAppConfig
import com.datadog.gradle.config.taskConfig

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.github.ben-manes.versions")
    id("thirdPartyLicences")
    id("org.jetbrains.dokka")
    id("com.google.devtools.ksp")
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
        multiDexEnabled = true

        vectorDrawables.useSupportLibrary = true

        val config = sampleAppConfig(project.rootDir, "tv")
        buildConfigField(
            "String",
            "DD_RUM_APPLICATION_ID",
            "\"${config.rumApplicationId}\""
        )
        buildConfigField(
            "String",
            "DD_CLIENT_TOKEN",
            "\"${config.token}\""
        )
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }
}

repositories {
    maven { setUrl("https://jitpack.io") }
}

dependencies {

    implementation(project(":dd-sdk-android-core"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":features:dd-sdk-android-session-replay-material"))
    implementation(project(":integrations:dd-sdk-android-ktx"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))
    implementation(project(":integrations:dd-sdk-android-timber"))
    implementation(project(":integrations:dd-sdk-android-tv"))

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.androidXCore)
    implementation(libs.androidXCoreKtx)
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.2")

    // Network
    implementation(libs.okHttp)
    implementation(libs.gson)

    // Misc
    implementation(libs.timber)

    // Video
    implementation(libs.bundles.exoplayer)
    implementation("com.github.TeamNewPipe.NewPipeExtractor:extractor:v0.21.0")
    implementation("com.google.android.exoplayer:extension-okhttp:2.19.1")
}

kotlinConfig(evaluateWarningsAsErrors = false)
taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}
junitConfig()
javadocConfig()
dependencyUpdateConfig()
