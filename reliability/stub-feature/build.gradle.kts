/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")

    // Analysis tools
    id("com.github.ben-manes.versions")
}

android {
    namespace = "com.datadog.android.api.feature.stub"
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)

    // Testing
    implementation(libs.bundles.jUnit5)
    implementation(libs.bundles.testTools)
}

androidLibraryConfig()
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
dependencyUpdateConfig()
