/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
    alias(libs.plugins.composeCompilerPlugin)
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

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
android {
    namespace = "com.datadog.android.sessionreplay.compose"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":features:dd-sdk-android-session-replay"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.kotlin)
    implementation(libs.gson)

    implementation(platform(libs.androidXComposeBom))
    implementation(libs.bundles.androidXCompose)
    implementation(libs.androidXComposeMaterial)

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    unmock(libs.robolectric)
}

unMock {
    keep("android.graphics.Color")
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "Session Replay Extension Support for Jetpack Compose."
    )
}

taskConfig<KotlinCompile> {
    compilerOptions {
        // This integration intentionally accesses Kotlin-internal Compose APIs via INVISIBLE_*
        // suppressions, which KGP 2.2 reports with the ERROR_SUPPRESSION diagnostic.
        freeCompilerArgs.add("-Xwarning-level=ERROR_SUPPRESSION:disabled")
    }
}
