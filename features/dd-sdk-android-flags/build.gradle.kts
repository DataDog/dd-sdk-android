/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.utils.createJsonModelsGenerationTask

plugins {
    // Applied before the Android plugin on purpose (not under "Analysis tools"): AGP 9 applies
    // the Kotlin plugin itself, and ktlint-gradle 14.2.0 registers its Android source-set tasks
    // twice when it comes after the Kotlin plugin.
    id("ktlint")

    // Build
    id("com.android.library")
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

createJsonModelsGenerationTask("generateFlagsModelsFromJson") {
    inputDirPath = "src/main/json/flags"
    targetPackageName = "com.datadog.android.flags.model"
}

android {
    namespace = "com.datadog.android.flags"
}

dependencies {
    // datadog
    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))

    implementation(libs.gson)
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.androidXAnnotation)
    implementation(libs.androidXCollection)

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
    testImplementation(testFixtures(project(":features:dd-sdk-android-rum")))
    testImplementation(libs.okHttpMock)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("org.json")
}

datadogBuild {
    applyKotlinConfig()
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "The Feature Flags integration feature to use with the Datadog monitoring " +
            "library for Android applications."
    )
}
