/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    namespace = "com.datadog.android.sessionreplay"

    sourceSets.named("test") {
        // Required because AGP doesn't support kotlin test fixtures :/
        java.srcDir("${project.rootDir.path}/dd-sdk-android-core/src/testFixtures/kotlin")
    }

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(libs.okHttp)
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.androidXAppCompat)

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(libs.okHttp)
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
}

apply(from = "clone_session_replay_schema.gradle.kts")
apply(from = "generate_session_replay_models.gradle.kts")

androidLibraryConfig()
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The Session Replay feature to use with the Datadog monitoring " +
        "library for Android applications."
)
