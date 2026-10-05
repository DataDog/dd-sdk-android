/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.sampleAppConfig
import com.datadog.gradle.config.taskConfig
import java.io.File

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    id("datadogBuildConfig")
}

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK

        buildFeatures {
            buildConfig = true
        }

        vectorDrawables.useSupportLibrary = true
        val file = File(File(project.rootDir, "config"), "vendor-lib.json")
        val config = sampleAppConfig(file.path)
        buildConfigField(
            "String",
            "DD_CLIENT_TOKEN",
            "\"${config.token}\""
        )
    }

    namespace = "com.datadog.android.vendor.sample"

    compileOptions {
        java17()
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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

    packaging {
        resources {
            excludes += "META-INF/*"
        }
    }
}

dependencies {

    implementation(project(":features:dd-sdk-android-logs"))
    implementation(project(":features:dd-sdk-android-trace"))
    implementation(project(":features:dd-sdk-android-trace-otel"))
    implementation(project(":integrations:dd-sdk-android-okhttp"))

    implementation(libs.kotlin)

    // Ktor (local web server)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.ktorServer)
}

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
}

taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
    }
}
