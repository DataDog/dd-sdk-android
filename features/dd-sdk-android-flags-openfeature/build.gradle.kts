/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    // Applied before the Android plugin on purpose (not under "Analysis tools"): AGP 9 applies
    // the Kotlin plugin itself, and ktlint-gradle 14.2.0 registers its Android source-set tasks
    // twice when it comes after the Kotlin plugin.
    id("ktlint")

    // Build
    id("com.android.library")
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
    namespace = "com.datadog.android.flags.openfeature"
}

dependencies {
    // datadog
    api(project(":dd-sdk-android-core"))
    api(project(":features:dd-sdk-android-flags"))
    implementation(project(":dd-sdk-android-internal"))

    // OpenFeature SDK
    api(libs.openFeatureKotlinSdk)

    implementation(libs.kotlin)
    implementation(libs.coroutinesCore)
    implementation(libs.androidXAnnotation)

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(libs.coroutinesTest)
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
        "OpenFeature Provider integration for the Datadog Feature Flags " +
            "library for Android applications."
    )
}
