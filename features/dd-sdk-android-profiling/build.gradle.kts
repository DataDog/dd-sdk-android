/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektCustomConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka-javadoc")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")

    // Internal Generation
    id("com.datadoghq.dependency-license")
    id("apiSurface")
    id("transitiveDependencies")
    id("verificationXml")
    id("binary-compatibility-validator")
}

android {
    namespace = "com.datadog.android.profiling"
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.kotlin)
    implementation(libs.androidXCore)
    implementation(libs.androidXCoreKtx)
    implementation(libs.gson)
    implementation(libs.okHttp)

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
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
    unmock(libs.robolectric)
}

unMock {
    keep("android.os.BaseBundle")
    keep("android.os.Bundle")
    keep("android.os.Parcel")
    keepStartingWith("com.android.internal.util.")
    keepStartingWith("android.util.")
    keep("android.content.ComponentName")
    keep("android.content.ContentProvider")
    keep("android.content.IContentProvider")
    keep("android.content.ContentProviderNative")
    keep("android.net.Uri")
    keep("android.os.Handler")
    keep("android.os.IMessenger")
    keep("android.os.Looper")
    keep("android.os.Message")
    keep("android.os.MessageQueue")
    keep("android.os.SystemProperties")
    keep("android.view.DisplayEventReceiver")
    keepStartingWith("org.json")
}

apply(from = "generate_profiling_models.gradle.kts")
kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The Profiling feature to use with the Datadog monitoring " +
        "library for Android applications."
)
detektCustomConfig()
