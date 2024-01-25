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
import java.nio.file.Paths

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
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    defaultConfig {
        consumerProguardFiles(Paths.get(rootDir.path, "consumer-rules.pro").toString())
    }

    namespace = "com.datadog.android.rum"

    sourceSets.named("test") {
        // Required because AGP doesn't support kotlin test fixtures :/
        java.srcDir("${project.rootDir.path}/dd-sdk-android-core/src/testFixtures/kotlin")
    }
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.okHttp)

    // Android Instrumentation
    implementation(libs.androidXCore)
    implementation(libs.androidXMetrics)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXRecyclerView)

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
    testImplementation(libs.okHttp)
    testImplementation(libs.okHttpMock)
    testImplementation(libs.bundles.openTracing)
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

apply(from = "clone_rum_schema.gradle.kts")
apply(from = "clone_telemetry_schema.gradle.kts")
apply(from = "generate_rum_models.gradle.kts")
apply(from = "generate_telemetry_models.gradle.kts")

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The RUM feature to use with the Datadog monitoring " +
        "library for Android applications."
)
