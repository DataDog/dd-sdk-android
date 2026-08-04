/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import com.datadog.gradle.utils.cloneRumEventsFormat
import com.datadog.gradle.utils.createJsonModelsGenerationTask
import com.datadog.gradle.utils.createRumSchemaCloneTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("ktlint")
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
    id("detekt-conventions")
    id("test-pyramid-api-surface")
}

android {
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    namespace = "com.datadog.android.sessionreplay"
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.okHttp)
    implementation(libs.kotlin)
    implementation(libs.gson)
    implementation(libs.androidXAppCompat)

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
    unmock(libs.robolectric)
}

unMock {
    keep("android.widget.ImageView\$ScaleType")
    keep("android.graphics.Rect")
    keep("android.graphics.drawable.GradientDrawable")
}

createRumSchemaCloneTask("cloneSessionReplayRootSchemas") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/",
        destinationFolder = "src/main/json/schemas",
        excludedPrefixes = listOf(
            "profiling",
            "session-replay/",
            "rum",
            "mobile",
            "telemetry",
            "session-replay-schema",
            "session-replay-browser-schema"
        )
    )
}

createRumSchemaCloneTask("cloneSessionReplayMobileSchemas") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/session-replay/mobile",
        destinationFolder = "src/main/json/schemas/session-replay/mobile"
    )
}

createRumSchemaCloneTask("cloneSessionReplayCommonSchemas") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/session-replay/common",
        destinationFolder = "src/main/json/schemas/session-replay/common"
    )
}

tasks.register("cloneSessionReplaySchemas") {
    dependsOn("cloneSessionReplayRootSchemas")
    dependsOn("cloneSessionReplayMobileSchemas")
    dependsOn("cloneSessionReplayCommonSchemas")
}

createJsonModelsGenerationTask("generateSessionReplayModels") {
    inputDirPath = "src/main/json/schemas"
    targetPackageName = "com.datadog.android.sessionreplay.model"
}

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "The Session Replay feature to use with the Datadog monitoring " +
        "library for Android applications."
)
