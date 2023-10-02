/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.BuildConfigPropertiesKeys
import com.datadog.gradle.config.GradlePropertiesKeys
import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.config.setLibraryVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("thirdPartyLicences")
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

/**
 * Checks whether logcat logs should be enabled when building the release version of the library.
 * @return true if logcat logs should be enabled
 */
fun isLogEnabledInRelease(): String {
    return project.findProperty(GradlePropertiesKeys.FORCE_ENABLE_LOGCAT) as? String ?: "false"
}

android {
    namespace = "com.datadog.android"

    defaultConfig {
        buildFeatures {
            buildConfig = true
        }
        setLibraryVersion()
    }

    buildTypes {
        getByName("release") {
            buildConfigField(
                "Boolean",
                BuildConfigPropertiesKeys.LOGCAT_ENABLED,
                isLogEnabledInRelease()
            )
        }

        getByName("debug") {
            buildConfigField(
                "Boolean",
                BuildConfigPropertiesKeys.LOGCAT_ENABLED,
                "true"
            )
        }
    }
}

dependencies {
    implementation(libs.kotlin)

    // Network
    implementation(libs.okHttp)
    implementation(libs.gson)
    implementation(libs.kronosNTP)

    // Android Instrumentation
    implementation(libs.androidXAnnotation)
    implementation(libs.androidXCollection)
    implementation(libs.androidXWorkManager)

    // Generate NoOp implementations
    ksp(project(":tools:noopfactory"))

    // Lint rules
    lintPublish(project(":tools:lint"))

    // Testing
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)

    // Test Fixtures
//    testFixturesImplementation(project(":tools:unit")) {
//        attributes {
//            attribute(
//                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
//                objects.named("jvm")
//            )
//        }
//    }
    testFixturesImplementation(libs.kotlin)
    testFixturesImplementation(libs.bundles.jUnit5)
    testFixturesImplementation(libs.okHttp)
    testFixturesImplementation(libs.bundles.testTools)
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keep("android.os.Parcel")
    keepStartingWith("com.android.internal.util.")
    keepStartingWith("android.util.")
    keep("android.content.ComponentName")
    keep("android.os.Looper")
    keep("android.os.MessageQueue")
    keep("android.os.SystemProperties")
    keepStartingWith("org.json")
}

androidLibraryConfig()
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig("Datadog monitoring library for Android applications.")
