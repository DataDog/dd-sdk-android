import dev.detekt.gradle.Detekt

/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

plugins {
    id("dev.detekt")
}

dependencies {
    detektPlugins(project(":tools:detekt"))
}

val detektConfigFiles = listOf(
    rootProject.file("detekt.yml"),
    rootProject.file("detekt_custom_safe_calls_android.yml"),
    rootProject.file("detekt_custom_safe_calls_third_party.yml"),
    rootProject.file("detekt_custom_unsafe_calls.yml")
)

detekt {
    config.setFrom(detektConfigFiles)
    ignoredVariants.set(listOf("release"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        sarif.required = false
        html.required = false
        checkstyle.required = false
        markdown.required = false
    }
}

// Disable the default task named "detekt" since it does not run type resolution
// Use it purely as a nicely named umbrella task.
// `detektMain`/`detektTest` are the per-variant aggregate tasks registered by the Detekt Android
// integration; they carry the compile classpath, so they do run type resolution. Their nested
// components (unit tests, android tests, test fixtures) are covered by `detektTest`.
tasks.named<Detekt>("detekt") {
    isEnabled = false

    dependsOn(
        tasks.named("detektMain"),
        tasks.named("detektTest")
    )
}
