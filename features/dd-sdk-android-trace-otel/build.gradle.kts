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
    defaultConfig {
        buildFeatures {
            buildConfig = true
        }
        buildConfigField(
            "String",
            "OPENTELEMETRY_API_VERSION_NAME",
            "\"${libs.versions.openTelemetry.get()}\""
        )
        consumerProguardFiles("consumer-rules.pro")
    }
    namespace = "com.datadog.android.trace.opentelemetry"
    sourceSets.named("test") {
        // Required because AGP doesn't support kotlin test fixtures :/
        java.srcDir("${project.rootDir.path}/dd-sdk-android-core/src/testFixtures/kotlin")
    }
}

dependencies {
    api(project(":dd-sdk-android-core"))
    api(project(":features:dd-sdk-android-trace"))
    api(libs.openTelemetryApi)
    implementation(libs.kotlin)
    implementation(libs.androidXAnnotation)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    testImplementation(libs.systemStubsJupiter)

    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("org.json")
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The tracing library for Android, providing OpenTelemetry compatibility."
)
