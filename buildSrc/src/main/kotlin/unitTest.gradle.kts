/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import org.gradle.api.artifacts.VersionCatalogsExtension

// Adds the shared unit-test dependencies (JUnit 5 + the common test-tooling bundle: Elmyr, AssertJ,
// mockito-kotlin, …) to a module's `test` source set. Apply with `id("unitTest")`.
//
// `kotlin-stdlib` is deliberately not added — the Kotlin plugin already contributes it to every
// source set, so adding it again would duplicate it.

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
listOf("jUnit5", "testTools").forEach { bundleName ->
    libs.findBundle(bundleName).ifPresent { bundle ->
        dependencies.addProvider("testImplementation", bundle)
    }
}
