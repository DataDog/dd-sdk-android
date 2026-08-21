/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.config

import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.javadocConfig
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig
import com.datadog.gradle.config.publishingConfig
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

/**
 * The `datadogBuild { }` DSL. Each `applyXxx` call opts the module into one of the shared build
 * conventions; nothing is applied implicitly, so a module only gets what it asks for.
 *
 * The conventions read the project's extensions eagerly, so the block belongs *after* the
 * `android { }` / `dependencies { }` blocks it configures.
 */
abstract class DatadogBuildExtension @Inject constructor(
    private val project: Project
) {

    /** Aligns Kotlin compilation: bytecode target, API/language version and warning strictness. */
    @JvmOverloads
    fun applyKotlinConfig(
        evaluateWarningsAsErrors: Boolean = true,
        jvmBytecodeTarget: JvmTarget = JvmTarget.JVM_17
    ) {
        project.kotlinConfig(evaluateWarningsAsErrors, jvmBytecodeTarget)
    }

    /** Applies the shared `com.android.library` setup: SDK levels, source sets, lint and packaging. */
    fun applyAndroidLibraryConfig() {
        project.androidLibraryConfig()
    }

    /** Runs tests on the JUnit 5 platform and mirrors stderr of failing tests to the console. */
    fun applyJunitConfig() {
        project.junitConfig()
    }

    /** Points the Dokka `javadoc` publication at `build/reports/javadoc`. */
    fun applyJavadocConfig() {
        project.javadocConfig()
    }

    /** Restricts the dependency-updates report to stable releases. */
    fun applyDependencyUpdateConfig() {
        project.dependencyUpdateConfig()
    }

    /** Declares the Maven publication (POM metadata, sources/javadoc jars) and its signing. */
    @JvmOverloads
    fun applyPublishingConfig(
        projectDescription: String,
        customArtifactId: String = project.name
    ) {
        project.publishingConfig(projectDescription, customArtifactId)
    }
}
