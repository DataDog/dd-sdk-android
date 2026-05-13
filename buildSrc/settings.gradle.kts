/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

pluginManagement {
    repositories {
        // RUM-15510 TEST: Magic Mirror Depot proxy FIRST (only set in CI via
        // `.gitlab-ci.yml`). Testing whether Gradle falls through cleanly when depot
        // returns 404, so we can rely on depot as the primary plugin source.
        val pluginProxy = providers.gradleProperty("gradlePluginProxy").orNull
        if (!pluginProxy.isNullOrBlank()) maven(pluginProxy)
        val mavenProxy = providers.gradleProperty("mavenRepositoryProxy").orNull
        if (!mavenProxy.isNullOrBlank()) maven(mavenProxy)
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
