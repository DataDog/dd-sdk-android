/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.github.ben-manes.versions")
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.kotlinReflect)
    implementation(libs.androidXAnnotation)
    compileOnly(libs.detektApi)
    // HotMethod and its CHECK_* companion constants live in dd-sdk-android-internal.
    // The constants are `const val` strings — the Kotlin compiler inlines them, so
    // this module has no runtime dependency on Android classes.
    compileOnly(project(":dd-sdk-android-internal"))

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.detektTest)
    testImplementation(libs.robolectric)
    testImplementation(project(":dd-sdk-android-internal"))
}

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
