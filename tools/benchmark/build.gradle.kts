/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java11
import com.datadog.gradle.utils.createJsonModelsGenerationTask

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    id("datadogBuildConfig")

    // Publishing
    `maven-publish`
    signing
}

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
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

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
    applyAndroidLibraryConfig()
    applyPublishingConfig(
        projectDescription = "An internal benchmarking tool to measure the overhead of Datadog SDK",
        customArtifactId = "dd-sdk-android-benchmark-internal"
    )
}
