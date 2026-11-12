/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply ktlint before AGP: built-in Kotlin otherwise causes duplicate ktlint tasks.
    id("ktlint")

    // Build
    id("com.android.library")
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
    id("test-pyramid-api-surface")
}

android {
    namespace = "com.datadog.android.compose"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(libs.kotlinRuntime)

    implementation(platform(libs.androidXComposeBom))
    implementation(libs.androidXComposeRuntime)
    implementation(libs.androidXComposeMaterial)
    implementation(libs.androidXComposeNavigation)

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    unmock(libs.robolectric)
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keepStartingWith("android.util")
    keepStartingWith("com.android.internal.util")
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "A Jetpack Compose integration to use with the Datadog monitoring library" +
            " for Android applications."
    )
}

taskConfig<KotlinCompile> {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
        // This integration intentionally accesses Kotlin-internal Compose APIs via INVISIBLE_*
        // suppressions, which KGP 2.2 reports with the ERROR_SUPPRESSION diagnostic.
        freeCompilerArgs.add("-Xwarning-level=ERROR_SUPPRESSION:disabled")
    }
}
