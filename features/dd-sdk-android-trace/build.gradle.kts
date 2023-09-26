/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
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
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    namespace = "com.datadog.android.trace"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.androidXAnnotation)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    // OpenTracing
    api(libs.bundles.openTracing)

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
    unmock(libs.robolectric)

    // TODO MTG-12 detekt(project(":tools:detekt"))
    // TODO MTG-12 detekt(libs.detektCli)
}

unMock {
    keepStartingWith("org.json")
}

apply(from = "clone_dd_trace.gradle.kts")
apply(from = "generate_trace_models.gradle.kts")

androidLibraryConfig()
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The Tracing feature to use with the Datadog monitoring " +
        "library for Android applications."
)
