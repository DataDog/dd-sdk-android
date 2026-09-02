/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

pluginManagement {
    repositories {
        // Magic Mirror Depot proxy (only set in CI via `.gitlab-ci.yml`).
        listOf("gradlePluginProxy", "mavenRepositoryProxy")
            .mapNotNull { providers.gradleProperty(it).orNull?.takeIf { url -> url.isNotBlank() } }
            .forEach { url -> maven(url) }
        gradlePluginPortal()
        google()
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
