/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("datadogBuildConfig")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka-javadoc")

    // Analysis tools
    id("detekt-conventions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("apiSurface")
    id("aarMetadata")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
    id("test-pyramid-api-surface")
}

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
android {
    namespace = "com.datadog.android.trace.api"
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.androidXAnnotation)
    implementation(libs.bundles.traceCore)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))
}

unMock {
    keepStartingWith("org.json")
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "Tracing engine API specification used for internal module communication."
    )
}

taskConfig<Test> {
    // this module has no tests
    failOnNoDiscoveredTests = false
}
