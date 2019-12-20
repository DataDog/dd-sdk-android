/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import com.datadog.gradle.Dependencies

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            val version = when (requested.id.id) {
                Dependencies.PluginIds.Android -> Dependencies.Versions.AndroidToolsPlugin
                Dependencies.PluginIds.KotlinJVM,
                Dependencies.PluginIds.KotlinAndroid,
                Dependencies.PluginIds.KotlinAndroidExtension -> Dependencies.Versions.Kotlin
                Dependencies.PluginIds.Detetk -> Dependencies.Versions.Detekt
                Dependencies.PluginIds.DependencyVersion -> Dependencies.Versions.DependencyVersion
                Dependencies.PluginIds.KtLint -> Dependencies.Versions.KtLint
                Dependencies.PluginIds.Bintray -> Dependencies.Versions.Bintray

                else -> {
                    if (
                        requested.id.namespace != Dependencies.PluginNamespaces.Gradle &&
                        requested.id.namespace != null
                    ) {
                        println("✗ unknown plugin ${requested.id.namespace}.${requested.id.name}")
                    }
                    null
                }
            }
            version?.let { useVersion(it) }
        }
    }
}

include(":dd-sdk-android")
include(":dd-sdk-android-timber")

include(":instrumented:benchmark")
include(":instrumented:integration")

include(":sample:java")
include(":sample:kotlin-timber")

include(":tools:detekt")
include(":tools:unit")
