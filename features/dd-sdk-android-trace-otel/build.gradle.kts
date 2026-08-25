/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
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
        buildFeatures {
            buildConfig = true
        }
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField(
            "String",
            "OPENTELEMETRY_API_VERSION_NAME",
            "\"${libs.versions.openTelemetry.get()}\""
        )
    }
    namespace = "com.datadog.android.trace.opentelemetry"
}

dependencies {
    api(libs.openTelemetryApi)
    implementation(libs.kotlin)
    implementation(libs.androidXAnnotation)

    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-trace"))

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-trace")))
    testImplementation(libs.systemStubsJupiter)

    unmock(libs.robolectric)
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
        "The tracing library for Android, providing OpenTelemetry compatibility."
    )
}
