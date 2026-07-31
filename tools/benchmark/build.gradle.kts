/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java11
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.utils.createJsonModelsGenerationTask

plugins {
    id("ktlint")
    id("com.android.library")
    kotlin("android")
    id("com.github.ben-manes.versions")

    `maven-publish`
    signing
}

android {
    defaultConfig {
        compileSdk = AndroidConfig.TARGET_SDK
        minSdk = AndroidConfig.MIN_SDK
    }
    namespace = "com.datadog.tools.benchmark"
    compileOptions {
        java11()
    }
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.openTelemetryApi)
    implementation(libs.openTelemetrySdk)
    implementation(libs.gson)
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
}

createJsonModelsGenerationTask("generateTraceModelsFromJson") {
    inputDirPath = "src/main/json"
    targetPackageName = "com.datadog.benchmark.internal.model"
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
androidLibraryConfig()
publishingConfig(
    projectDescription = "An internal benchmarking tool to measure the overhead of Datadog SDK",
    customArtifactId = "dd-sdk-android-benchmark-internal"
)
