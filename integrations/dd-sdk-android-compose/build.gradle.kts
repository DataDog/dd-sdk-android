/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.taskConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    id("test-pyramid-api-surface")
}

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
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
    implementation(libs.kotlin)

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
    applyKotlinConfig(
        // TODO RUM-18191
        // Suppress -> generateFunctionKeyMetaClasses is deprecated. It was replaced by emitting annotations on functions
        // instead. Use generateFunctionKeyMetaAnnotations instead. Seems to Compose <-> Kotlin mismatch.
        evaluateWarningsAsErrors = false,
        jvmBytecodeTarget = JvmTarget.JVM_11
    )
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "A Jetpack Compose integration to use with the Datadog monitoring library" +
            " for Android applications."
    )
}

taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        optIn.add("kotlin.RequiresOptIn")
    }
}
