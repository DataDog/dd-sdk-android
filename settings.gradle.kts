/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

import com.datadog.gradle.Dependencies

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == Dependencies.PluginNamespaces.Kotlin) {
                useVersion(Dependencies.Versions.Kotlin)
            } else if (requested.id.namespace == Dependencies.PluginNamespaces.KotlinAndroid) {
                useVersion(Dependencies.Versions.Kotlin)
            } else if (requested.id.namespace == Dependencies.PluginNamespaces.Detetk) {
                useVersion(Dependencies.Versions.Detekt)
            } else if (requested.id.namespace == Dependencies.PluginNamespaces.DependencyVersion) {
                useVersion(Dependencies.Versions.DependencyVersion)
            } else if (requested.id.namespace == Dependencies.PluginNamespaces.KtLint) {
                useVersion(Dependencies.Versions.KtLint)
            } else if (requested.id.namespace == Dependencies.PluginNamespaces.Gradle) {
                // Do nothing, plugin handled by Gradle
            } else {
                println("⋄⋄⋄ namespace:${requested.id.namespace} / name:${requested.id.name}")
            }
        }
    }
}

include(":dd-sdk-android")
