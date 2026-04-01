/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")

    // Analysis tools
    id("com.github.ben-manes.versions")
}

android {

    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION
    namespace = "com.datadog.android.reliability.withbackend"

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        if (project.hasProperty(com.datadog.gradle.Properties.USE_DESUGARING)) {
            isCoreLibraryDesugaringEnabled = true
        }
        java17()
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/jvm.kotlin_module",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            testProguardFile("test-proguard-rules.pro")
        }
    }
}

dependencies {
    if (project.hasProperty(com.datadog.gradle.Properties.USE_DESUGARING)) {
        coreLibraryDesugaring(libs.androidDesugaringSdk)
    }
    implementation(libs.kotlin)

    // Testing
    androidTestImplementation(libs.assertJ)
    androidTestImplementation(libs.bundles.integrationTests)
    androidTestImplementation(project(":features:dd-sdk-android-rum"))
    androidTestImplementation(libs.coroutinesCore)
    androidTestImplementation(libs.bundles.ktorClient)
    androidTestImplementation(libs.kotlinxSerializationJson)
    if (project.hasProperty(com.datadog.gradle.Properties.USE_API21_JAVA_BACKPORT)) {
        // this is needed to make AssertJ working on APIs <24
        androidTestImplementation(project(":tools:javabackport"))
    }
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
dependencyUpdateConfig()
