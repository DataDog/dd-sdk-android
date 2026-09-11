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
    id("org.jetbrains.dokka")

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
    namespace = "com.datadog.android.apollo"
}

dependencies {
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.apolloRuntime)
    implementation(libs.kotlin)
    implementation(libs.okHttp)

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(libs.okHttpMock)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("android.util.")
}

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
    applyAndroidLibraryConfig()
    applyPublishingConfig(
        projectDescription = "An Apollo interceptor for handling GraphQL requests to use with the " +
            "Datadog monitoring library for Android applications."
    )
}
