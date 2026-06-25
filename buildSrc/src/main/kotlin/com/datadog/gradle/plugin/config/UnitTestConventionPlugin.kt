/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.config

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Convention plugin that adds the shared unit-test dependencies to a module's `test` source set:
 * the JUnit 5 bundle and the common test-tooling bundle (Elmyr, AssertJ, mockito-kotlin, …).
 *
 * Apply it with `plugins { id("datadog.unit-test") }` to give a module the standard unit-test stack.
 *
 * `kotlin-stdlib` is deliberately not added here — the Kotlin plugin already contributes it to every
 * source set (`kotlin.stdlib.default.dependency`), so adding it again would duplicate it.
 */
class UnitTestConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val libs = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
        BUNDLES.forEach { bundleName ->
            libs.findBundle(bundleName).ifPresent { bundle ->
                target.dependencies.addProvider("testImplementation", bundle)
            }
        }
    }

    private companion object {
        val BUNDLES = listOf("jUnit5", "testTools")
    }
}
