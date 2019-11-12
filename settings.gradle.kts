/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import com.datadog.gradle.Dependencies

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.namespace) {
                Dependencies.PluginNamespaces.Kotlin -> useVersion(Dependencies.Versions.Kotlin)
                Dependencies.PluginNamespaces.KotlinAndroid -> useVersion(Dependencies.Versions.Kotlin)
                Dependencies.PluginNamespaces.Detetk -> useVersion(Dependencies.Versions.Detekt)
                Dependencies.PluginNamespaces.DependencyVersion -> useVersion(Dependencies.Versions.DependencyVersion)
                Dependencies.PluginNamespaces.KtLint -> useVersion(Dependencies.Versions.KtLint)
                Dependencies.PluginNamespaces.Gradle -> {
                    // Do nothing, plugin handled by Gradle
                }
                else -> println("⋄⋄⋄ namespace:${requested.id.namespace} / name:${requested.id.name}")
            }
        }
    }
}

include(":dd-sdk-android")
