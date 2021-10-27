/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.jacocoConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.ktLintConfig
import com.datadog.gradle.config.setLibraryVersion

plugins {
    // Build
    id("com.android.library")
    kotlin("android")

    // Publish
    // TODO RUMM-1612 enable these back once we have meaningful content to publish
    // `maven-publish`
    // signing
    // id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")

    // Tests
    jacoco

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK_FOR_COMPOSE
        targetSdk = AndroidConfig.TARGET_SDK
        setLibraryVersion()
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.0.2"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        isWarningsAsErrors = true
        isAbortOnError = true
        isCheckReleaseBuilds = false
        isCheckGeneratedSources = true
    }
}

dependencies {
    api(project(":dd-sdk-android"))
    implementation(libs.kotlin)
    implementation(libs.androidXComposeRuntime)

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
jacocoConfig()
javadocConfig()
dependencyUpdateConfig()
// TODO RUMM-1612 uncomment once we have meaningful content in the module
// publishingConfig(
//    "A Jetpack Compose integration to use with the Datadog monitoring library for Android applications."
// )
