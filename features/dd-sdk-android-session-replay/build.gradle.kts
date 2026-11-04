/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.utils.cloneRumEventsFormat
import com.datadog.gradle.utils.createJsonModelsGenerationTask
import com.datadog.gradle.utils.createRumEventsFormatCloneTask
import org.gradle.api.component.AdhocComponentWithVariants
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Apply ktlint before AGP: built-in Kotlin otherwise causes duplicate ktlint tasks.
    id("ktlint")

    // Build
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("datadogBuildConfig")

    // Publish
    `maven-publish`
    signing
    id("org.jetbrains.dokka-javadoc")

    // Analysis tools
    id("detekt-conventions")

    // Tests
    id("de.mobilej.unmock")
    id("org.jetbrains.kotlinx.kover")
    id("unitTest")

    // Internal Generation
    id("apiSurface")
    id("aarMetadata")
    id("transitiveDependencies")
    id("verificationXml")
    id("binary-compatibility-validator")
    id("test-pyramid-api-surface")
}

android {
    testFixtures {
        enable = true
    }

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    namespace = "com.datadog.android.sessionreplay"
}

dependencies {
    testFixturesImplementation(libs.kotlinRuntime)
    testFixturesImplementation(testFixtures(project(":dd-sdk-android-core")))

    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.okHttp)
    implementation(libs.kotlinRuntime)
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
    testImplementation(testFixtures(project(":dd-sdk-android-internal")))
    unmock(libs.robolectric)
}

unMock {
    keep("android.widget.ImageView\$ScaleType")
    keep("android.graphics.Rect")
    keep("android.graphics.drawable.GradientDrawable")
}

createRumEventsFormatCloneTask("cloneSessionReplayRootSchemas") {
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

createRumEventsFormatCloneTask("cloneSessionReplayMobileSchemas") {
    cloneRumEventsFormat(
        project = project,
        subFolder = "schemas/session-replay/mobile",
        destinationFolder = "src/main/json/schemas/session-replay/mobile"
    )
}

createRumEventsFormatCloneTask("cloneSessionReplayCommonSchemas") {
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

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "The Session Replay feature to use with the Datadog monitoring " +
            "library for Android applications."
    )
}

// Keep the image-capture fixtures local to this build.
afterEvaluate {
    val releaseComponent = components["release"] as AdhocComponentWithVariants
    listOf("Api", "Runtime").forEach { usage ->
        releaseComponent.withVariantsFromConfiguration(
            configurations["releaseTestFixturesVariantRelease${usage}Publication"]
        ) { skip() }
    }
}
