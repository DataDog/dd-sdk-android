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
 * `mavenRepositoryProxy` Gradle properties are set) before invoking [configure] to
 * declare the regular fallback repositories.
 *
 * The proxy properties are only set in CI (via `.gitlab-ci.yml`), so local builds keep
 * resolving from public registries unchanged.
 *
 * Usage:
 * ```
 * allprojects {
 *     repositories.depotProxied(providers) {
 *         google()
 *         mavenCentral()
 *     }
 * }
 * ```
 */
fun RepositoryHandler.depotProxied(
    providers: ProviderFactory,
    configure: RepositoryHandler.() -> Unit
) {
    val pluginProxy = providers.gradleProperty(GradlePropertiesKeys.GRADLE_PLUGIN_PROXY).orNull
    if (!pluginProxy.isNullOrBlank()) {
        maven(org.gradle.api.Action<MavenArtifactRepository> { setUrl(pluginProxy) })
    }
    val mavenProxy = providers.gradleProperty(GradlePropertiesKeys.MAVEN_REPOSITORY_PROXY).orNull
    if (!mavenProxy.isNullOrBlank()) {
        maven(org.gradle.api.Action<MavenArtifactRepository> { setUrl(mavenProxy) })
    }
    configure()
}
