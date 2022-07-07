/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.config.setLibraryVersion

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
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        setLibraryVersion()
    }

    sourceSets.named("main") {
        java.srcDir("src/main/kotlin")
    }
    sourceSets.named("test") {
        java.srcDir("src/test/kotlin")
    }
    sourceSets.named("androidTest") {
        java.srcDir("src/androidTest/kotlin")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = false
        checkGeneratedSources = true
        ignoreTestSources = true
    }
}

dependencies {
    implementation(libs.kotlin)

    testImplementation(project(":tools:unit"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)

    detekt(project(":tools:detekt"))
    detekt(libs.detektCli)
}

kotlinConfig()
detektConfig()
ktLintConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The Session Replay feature to use with the Datadog monitoring " +
        "library for Android applications."
)
