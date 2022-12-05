/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import com.datadog.gradle.Dependencies
import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
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
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        setLibraryVersion()
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                version = Dependencies.Versions.CMake
            }
        }
    }

    namespace = "com.datadog.android.ndk"

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

    externalNativeBuild {
        cmake {
            path = File("$projectDir/CMakeLists.txt")
            version = Dependencies.Versions.CMake
        }
    }

    packagingOptions {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }

    ndkVersion = Dependencies.Versions.Ndk
}

dependencies {
    api(project(":dd-sdk-android"))
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.androidXMultidex)

    testImplementation(project(":tools:unit"))
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)

    androidTestImplementation(project(":tools:unit"))
    androidTestImplementation(libs.bundles.integrationTests)
    androidTestImplementation(libs.gson)
    androidTestImplementation(libs.assertJ)

    if (project.hasProperty(com.datadog.gradle.Properties.USE_API21_JAVA_BACKPORT)) {
        // this is needed to make AssertJ working on APIs <24
        androidTestImplementation(project(":tools:javabackport"))
    }

    detekt(project(":tools:detekt"))
    detekt(libs.detektCli)
}

kotlinConfig()
detektConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "An NDK integration to use with the Datadog monitoring library for Android applications."
)
