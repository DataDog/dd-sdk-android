/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.provider.ProviderFactory

/**
 * Prepends the Magic Mirror Depot proxy repositories (when the `gradlePluginProxy` and
 * `mavenRepositoryProxy` Gradle properties are set) at the CURRENT position in the
 * RepositoryHandler. Callers are responsible for declaring `google()`, `mavenCentral()`,
 * `mavenLocal()`, etc. around this call.
 *
 * The depot proxies Maven Central and the Gradle Plugin Portal, but NOT Google Maven
 * (dl.google.com / maven.google.com). Therefore `google()` MUST be declared BEFORE this
 * function is called, so that AndroidX / Compose / AGP artifacts resolve directly from
 * Google's repository and do not 404 at depot.
 *
 * The proxy properties are only set in CI (via `.gitlab-ci.yml`), so local builds keep
 * resolving from public registries unchanged.
 *
 * Usage:
 * ```
 * allprojects {
 *     repositories {
 *         google()                         // direct, NOT proxied
 *         depotProxy(providers)            // depot prepended here (CI only)
 *         mavenCentral()                   // fallback after depot
 *         maven { setUrl(JITPACK_URL) }    // direct, NOT proxied
 *     }
 * }
 * ```
 */
fun RepositoryHandler.depotProxy(providers: ProviderFactory) {
    val pluginProxy = providers.gradleProperty(GradlePropertiesKeys.GRADLE_PLUGIN_PROXY).orNull
    if (!pluginProxy.isNullOrBlank()) {
        maven(org.gradle.api.Action<MavenArtifactRepository> { setUrl(pluginProxy) })
    }
    val mavenProxy = providers.gradleProperty(GradlePropertiesKeys.MAVEN_REPOSITORY_PROXY).orNull
    if (!mavenProxy.isNullOrBlank()) {
        maven(org.gradle.api.Action<MavenArtifactRepository> { setUrl(mavenProxy) })
    }
}
