/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektCustomConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka-javadoc")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("com.datadoghq.dependency-license")
    id("apiSurface")
    id("transitiveDependencies")
    id("verificationXml")
    id("binary-compatibility-validator")
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
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
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

apply(from = "clone_common_schema.gradle.kts")
apply(from = "generate_trace_models.gradle.kts")

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The Tracing feature to use with the Datadog monitoring " +
        "library for Android applications."
)
detektCustomConfig()
