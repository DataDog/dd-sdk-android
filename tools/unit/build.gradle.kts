/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java11
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.github.ben-manes.versions")
    id("thirdPartyLicences")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
    }

    namespace = "com.datadog.tools.unit"

    compileOptions {
        java11()
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
        java.srcDir("src/main/java")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    flavorDimensions += "platform"
    productFlavors {
        register("art") {
            isDefault = false
        }
        register("jvm") {
            isDefault = true
        }
    }
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinReflect)
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
    implementation(libs.gson)

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
