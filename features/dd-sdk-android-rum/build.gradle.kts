/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.detektCustomConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.utils.cloneRumEventsFormat
import com.datadog.gradle.utils.createJsonModelsGenerationTask
import com.datadog.gradle.utils.createRumSchemaCloneTask
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
    id("org.jetbrains.dokka-javadoc")

    // Analysis tools
    id("com.github.ben-manes.versions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")
    id("unitTest")

    // Internal Generation
    id("apiSurface")
    id("transitiveDependencies")
    id("verificationXml")
    id("binary-compatibility-validator")
}

android {
    defaultConfig {
        consumerProguardFiles(
            Paths.get(rootDir.path, "consumer-rules.pro").toString(),
            "consumer-rules.pro"
        )
    }

    namespace = "com.datadog.android.rum"

    testFixtures {
        enable = true
    }
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.okHttp)

    // Android Instrumentation
    implementation(libs.androidXCore)
    implementation(libs.androidXMetrics)
    implementation(libs.bundles.androidXNavigation)
    implementation(libs.androidXRecyclerView)
    implementation(libs.androidXFragment)

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

    testImplementation(libs.okHttpMock)
    testImplementation(project(":features:dd-sdk-android-trace"))
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(testFixtures(project(":dd-sdk-android-internal")))
    testImplementation(testFixtures(project(":features:dd-sdk-android-trace")))
    unmock(libs.robolectric)

    // Test Fixtures
    testFixturesImplementation(testFixtures(project(":dd-sdk-android-core")))
    testFixturesImplementation(testFixtures(project(":dd-sdk-android-internal")))
    testFixturesImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
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

createRumSchemaCloneTask("cloneRumSchema") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/rum",
        destinationFolder = "src/main/json/rum"
    )
}

createRumSchemaCloneTask("cloneTelemetrySchema") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/telemetry",
        destinationFolder = "src/main/json/telemetry"
    )
}

createJsonModelsGenerationTask("generateRumModelsFromJson") {
    inputDirPath = "src/main/json/rum"
    targetPackageName = "com.datadog.android.rum.model"
    ignoredFiles = listOf(
        "_common-schema.json",
        "_action-child-schema.json",
        "_perf-metric-schema.json",
        "_profiling-internal-context-schema.json",
        "_rect-schema.json",
        "_view-container-schema.json",
        "_view-accessibility-schema.json",
        "_view-performance-schema.json",
        "_view-properties-schema.json",
        "_vital-common-schema.json"
    )
    inputNameMapping = mapOf(
        "action-schema.json" to "ActionEvent",
        "error-schema.json" to "ErrorEvent",
        "resource-schema.json" to "ResourceEvent",
        "view-schema.json" to "ViewEvent",
        "view_update-schema.json" to "ViewUpdateEvent",
        "long_task-schema.json" to "LongTaskEvent",
        "vital-app-launch-schema.json" to "VitalAppLaunchEvent",
        "vital-operation-step-schema.json" to "VitalOperationStepEvent",
        "timeseries-memory-schema.json" to "TimeseriesMemoryEvent",
        "timeseries-cpu-schema.json" to "TimeseriesCpuEvent"

    )
}

createJsonModelsGenerationTask("generateTelemetryModelsFromJson") {
    inputDirPath = "src/main/json/telemetry"
    targetPackageName = "com.datadog.android.telemetry.model"
    ignoredFiles = listOf(
        "_common-schema.json"
    )
    inputNameMapping = mapOf(
        "debug-schema.json" to "TelemetryDebugEvent",
        "error-schema.json" to "TelemetryErrorEvent"
    )
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The RUM feature to use with the Datadog monitoring " +
        "library for Android applications."
)
detektCustomConfig()
