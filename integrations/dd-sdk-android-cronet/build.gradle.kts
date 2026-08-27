/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

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

// TODO RUM-18189 Support new AGP DSL
@Suppress("DEPRECATION")
android {
    namespace = "com.datadog.android.cronet"
    lint {
        // Cronet library has experimental annotations that AndroidX lint checker
        // cannot properly parse, causing "Failed to extract attribute 'level'" error
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    api(libs.cronetApi)
    implementation(libs.kotlin)
    implementation(libs.androidXAnnotation)

    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-trace"))

    unmock(libs.robolectric)
    // Trying to add most recent lib version in order to test the instrumentation, see CronetApiInstrumentationTest
    testImplementation("${libs.cronetApi.get().module}:+")
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(testFixtures(project(":dd-sdk-android-internal")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-rum")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-trace")))

    testImplementation(libs.elmyrJUnit4)
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
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
        "A Cronet monitoring integration to use with the Datadog monitoring library for Android applications."
    )
}
