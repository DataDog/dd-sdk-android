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
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask
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
    id("datadog.unit-test")

    // Internal Generation
    id("apiSurface")
    id("transitiveDependencies")
    id("binary-compatibility-validator")
}

android {
    namespace = "com.datadog.android.trace.internal"

    libraryVariants.all {
        packageLibraryProvider.configure {
            from("src/main/resources") {
                include("META-INF/**/verification.properties")
            }
        }
    }
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

kotlinConfig(jvmBytecodeTarget = JvmTarget.JVM_11)
androidLibraryConfig()
junitConfig()
javadocConfig()
dependencyUpdateConfig()
publishingConfig(
    "Internal APM support library for Android applications."
)
detektCustomConfig()
