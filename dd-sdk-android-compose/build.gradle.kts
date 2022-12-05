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
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.config.setLibraryVersion
import com.datadog.gradle.config.taskConfig

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

    // Tests
    id("de.mobilej.unmock")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("wiki")
}

android {
    compileSdk = AndroidConfig.TARGET_SDK
    buildToolsVersion = AndroidConfig.BUILD_TOOLS_VERSION

    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK_FOR_COMPOSE
        targetSdk = AndroidConfig.TARGET_SDK
        setLibraryVersion()
    }

    namespace = "com.datadog.android.compose"

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidXComposeRuntime.get()
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
    }
}

dependencies {
    api(project(":dd-sdk-android"))
    implementation(libs.kotlin)
    implementation(libs.androidXComposeRuntime)
    implementation(libs.androidXComposeMaterial)
    implementation(libs.androidXComposeNavigation)

    testImplementation(project(":tools:unit"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)

    detekt(project(":tools:detekt"))
    detekt(libs.detektCli)
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
}

kotlinConfig()
taskConfig<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}
detektConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "A Jetpack Compose integration to use with the Datadog monitoring library" +
        " for Android applications."
)
