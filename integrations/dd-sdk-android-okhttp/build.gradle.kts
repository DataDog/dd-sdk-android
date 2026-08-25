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
    namespace = "com.datadog.android.okhttp"
}

dependencies {
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.androidXAnnotation)
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-rum"))
    // TODO RUM-15626: Move TraceContextInjection to dd-sdk-android-trace-api module,
    //  then replace api(dd-sdk-android-trace) with api(dd-sdk-android-trace-api).
    api(project(":features:dd-sdk-android-trace"))
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
    testImplementation(testFixtures(project(":dd-sdk-android-internal")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-rum")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-trace")))
    testImplementation(libs.okHttpMock)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("org.json")
    keepStartingWith("android.util.")
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "An OkHttp monitoring integration to use with the Datadog monitoring library for Android applications."
    )
}
