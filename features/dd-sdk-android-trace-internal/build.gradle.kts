/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("StringLiteralDuplication")

import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Build
    id("com.android.library")
    // Applied before `kotlin("android")` on purpose (not under "Analysis tools"): ktlint-gradle
    // 14.2.0 registers its Android source-set tasks twice when it comes after the Kotlin plugin.
    id("ktlint")
    kotlin("android")
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
    id("transitiveDependencies")
    id("binary-compatibility-validator")
    id("test-pyramid-api-surface")
}

android {
    namespace = "com.datadog.android.trace.internal"
}

dependencies {
    api(project(":dd-sdk-android-core"))
    implementation(project(":dd-sdk-android-internal"))
    implementation(libs.gson)
    implementation(libs.androidXAnnotation)
    implementation(libs.bundles.traceCore)

    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }
    testImplementation(testFixtures(project(":dd-sdk-android-core")))
    testImplementation(libs.systemStubsJupiter)
}

unMock {
    keepStartingWith("org.json")
}

val ddTraceRepository = "https://github.com/DataDog/dd-trace-java.git"
val ddTraceVersion = "v0.50.0"

tasks.register<GitCloneDependenciesTask>("cloneDdTrace") {
    extension.apply {
        clone(
            ddTraceRepository,
            "dd-trace-ot",
            ddTraceVersion,
            listOf(
                "dd-trace-ot.gradle",
                "README.md",
                "jfr-openjdk/",
                "src/jmh/", // JVM based benchmark, not relevant for ART/Dalvik
                "src/traceAgentTest/",
                "src/ot33CompatabilityTest/",
                "src/ot31CompatabilityTest/",
                "src/test/resources/",
                "src/main/java/datadog/trace/common/processor/",
                "src/main/java/datadog/trace/common/sampling/RuleBasedSampler.java",
                "src/main/java/datadog/trace/common/serialization",
                "src/main/java/datadog/trace/common/writer/unixdomainsockets",
                "src/main/java/datadog/trace/common/writer/ddagent",
                "src/main/java/datadog/trace/common/writer/DDAgentWriter.java",
                "src/main/java/datadog/opentracing/resolver",
                "src/main/java/datadog/opentracing/ContainerInfo.java",
                "src/test"
            )
        )
        clone(
            ddTraceRepository,
            "dd-trace-api",
            ddTraceVersion,
            listOf(
                "dd-trace-api.gradle",
                "src/main/java/datadog/trace/api/GlobalTracer.java",
                "src/main/java/datadog/trace/api/CorrelationIdentifier.java",
                "src/test"
            )
        )
        clone(
            ddTraceRepository,
            "utils/thread-utils",
            ddTraceVersion,
            listOf(
                "thread-utils.gradle",
                "src/test/"
            )
        )
    }
    projectDirPath.set(project.layout.projectDirectory.asFile.path)
}

datadogBuild {
    applyKotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
    applyAndroidLibraryConfig()
    applyJunitConfig()
    applyJavadocConfig()
    applyPublishingConfig(
        "Internal APM support library for Android applications."
    )
}
