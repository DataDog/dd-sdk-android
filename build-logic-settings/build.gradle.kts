/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import dev.detekt.gradle.Detekt

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlintGradlePlugin)
    alias(libs.plugins.detektGradlePlugin)
}

detekt {
    config.setFrom(file("../detekt.yml"))
    basePath = layout.projectDirectory.dir("..")
}

tasks.withType<Detekt>().configureEach {
    // Gradle scripts need DSL context. The root detektScripts task checks them separately.
    exclude("**/*.kts")
    val buildDirectory = layout.buildDirectory
    exclude { it.file.startsWith(buildDirectory.get().asFile) }
    reports {
        sarif.required = false
        html.required = false
        markdown.required = false
        checkstyle.required = false
    }
}

tasks.named<Detekt>("detekt") {
    enabled = false
    dependsOn(tasks.named { it in setOf("detektMain") })
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
