/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.utils.createJsonModelsGenerationTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.file.Paths

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
    id("unitTest")

    // Internal Generation
    id("apiSurface")
    id("aarMetadata")
    id("transitiveDependencies")
    id("verificationXml")
    id("binary-compatibility-validator")
    id("test-pyramid-api-surface")
}

android {
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    namespace = "com.datadog.android.trace"

    testFixtures {
        enable = true
    }
}

dependencies {
    api(project(":dd-sdk-android-core"))
    api(project(":features:dd-sdk-android-trace-api"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-trace-internal"))
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.androidXAnnotation)
    implementation(libs.bundles.traceCore)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(libs.okHttp)
    testImplementation(libs.systemStubsJupiter)
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }

    unmock(libs.robolectric)

    // Test Fixtures
    testFixturesImplementation(libs.gson)
    testFixturesImplementation(libs.kotlin)
    testFixturesImplementation(libs.okHttp)
    testFixturesImplementation(libs.bundles.jUnit5)
    testFixturesImplementation(libs.bundles.testTools)
    testFixturesImplementation(project(":features:dd-sdk-android-trace-internal"))
}

unMock {
    keepStartingWith("org.json")
}

createJsonModelsGenerationTask("generateTraceModelsFromJson") {
    inputDirPath = "src/main/json/trace"
    ignoredFiles = listOf(
        "_common-schema.json"
    )
    targetPackageName = "com.datadog.android.trace.model"
    extraInputWatchDir = project.layout.projectDirectory.dir(
        Paths.get("../dd-sdk-android-rum/src/main/json/rum").toString()
    )
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "The Tracing feature to use with the Datadog monitoring " +
            "library for Android applications."
    )
}
