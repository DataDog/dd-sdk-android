/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config
object GradlePropertiesKeys {

    // you can set this property from your gradle.properties as: forceEnableLogcat = true | false
    const val FORCE_ENABLE_LOGCAT = "forceEnableLogcat"

    /**
     * Maven repository proxy URL (Magic Mirror Depot). When set, prepended to dependency
     * repositories so artifact resolution goes through Datadog's internal mirror instead of
     * hitting public registries (e.g. Maven Central) directly. Used in CI to avoid 429s.
     * Set via -PmavenRepositoryProxy=... or in gradle.properties.
     */
    const val MAVEN_REPOSITORY_PROXY = "mavenRepositoryProxy"

    /**
     * Gradle plugin portal proxy URL (Magic Mirror Depot). When set, prepended to plugin
     * resolution repositories so plugin resolution goes through Datadog's internal mirror
     * instead of hitting plugins.gradle.org directly. Used in CI to avoid 429s.
     * Set via -PgradlePluginProxy=... or in gradle.properties.
     */
    const val GRADLE_PLUGIN_PROXY = "gradlePluginProxy"
}
