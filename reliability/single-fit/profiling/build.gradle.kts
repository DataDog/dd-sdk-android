/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("ktlint")
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Analysis tools
    id("com.github.ben-manes.versions")
    id("test-pyramid-api-usage")

    // Tests
    id("de.mobilej.unmock")
}

android {
    namespace = "com.datadog.android.profiling.integration"
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(project(":features:dd-sdk-android-rum"))
    implementation(project(":features:dd-sdk-android-profiling"))
    implementation(libs.kotlin)

    // Testing
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
    testImplementation(testFixtures(project(":features:dd-sdk-android-profiling")))
    testImplementation(project(":reliability:stub-core"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
}

unMock {
    keepStartingWith("android.os")
    keepStartingWith("org.json")
}

androidLibraryConfig()
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
dependencyUpdateConfig()
