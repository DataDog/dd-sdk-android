/*
 * Unless explicitly stated otherwise all pomFilesList in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.configureFlavorForSampleApp
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("thirdPartyLicences")
    id("org.jetbrains.dokka")
    jacoco
    id("realm-android")
    id("com.squareup.sqldelight")
}

sqldelight {
    database("LogsDatabase") {
        packageName = "com.datadog.android.sample"
        dialect = "sqlite:3.24"
        sourceFolders = listOf("sqldelight")
    }
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = AndroidConfig.VERSION.code
        versionName = AndroidConfig.VERSION.name
        multiDexEnabled = true

        vectorDrawables.useSupportLibrary = true
        externalNativeBuild {
            cmake {
                cppFlags.add("-std=c++14")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    flavorDimensions("version")
    productFlavors {
        val regions = arrayOf("us1", "us3", "us5", "us1_fed", "eu1", "staging")

        regions.forEachIndexed { index, region ->
            register(region) {
                isDefault = index == 0
                dimension = "version"
                configureFlavorForSampleApp(this, projectDir)
            }
        }
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
        exclude("META-INF/*")
    }

    externalNativeBuild {
        cmake {
            path = File("$projectDir/src/main/cpp/CMakeLists.txt")
            version = Dependencies.Versions.CMake
        }
    }
    ndkVersion = Dependencies.Versions.Ndk
}

dependencies {

    api(project(":dd-sdk-android"))
    api(project(":dd-sdk-android-ktx"))
    api(project(":dd-sdk-android-ndk"))
    api(project(":dd-sdk-android-rx"))
    api(project(":dd-sdk-android-timber"))
    api(project(":dd-sdk-android-coil"))
    api(project(":dd-sdk-android-glide"))
    api(project(":dd-sdk-android-fresco"))
    api(project(":dd-sdk-android-sqldelight"))

    implementation(libs.kotlin)

    // Android dependencies
    implementation(libs.androidXMultidex)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXAppCompat)
    implementation(libs.androidXConstraintLayout)
    implementation(libs.googleMaterial)
    implementation("androidx.media:media:1.3.1")
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("io.opentracing.contrib:opentracing-rxjava-3:0.1.4") {
        exclude(group = "io.opentracing")
    }

    // Ktor (local web server)
    implementation(libs.bundles.ktor)

    // Image Loading Library
    implementation(libs.coil)
    implementation(libs.bundles.fresco)
    implementation(libs.bundles.glide)
    implementation(libs.picasso)
    kapt(libs.glideCompiler)

    // Local Storage
    implementation(libs.sqlDelight)
    implementation(libs.room)
    kapt(libs.roomCompiler)

    // Multithreading
    implementation(libs.rxJava3)
    implementation("com.squareup.retrofit2:adapter-rxjava3:2.9.0")
    implementation(libs.rxJava3Android)
    implementation(libs.bundles.coroutines)

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(libs.okHttp)
    implementation(libs.gson)

    // Misc
    implementation(libs.timber)
    api("com.facebook.stetho:stetho:1.5.1")
}

kotlinConfig(evaluateWarningsAsErrors = false)
detektConfig()
ktLintConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
