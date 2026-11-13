/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlintGradlePlugin)
}

dependencies {
    implementation(libs.s3BuildCache)
}

gradlePlugin {
    plugins {
        register("buildCache") {
            id = "com.datadog.gradle.build-cache"
            implementationClass = "com.datadog.gradle.plugin.cache.BuildCachePlugin"
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

ktlint {
    version = provider { libs.versions.ktlint.get() }
}
