/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import dev.detekt.gradle.Detekt

plugins {
    id("detekt-common")
}

dependencies {
    detektPlugins(project(":tools:detekt"))
}

val detektConfigFiles = listOf(
    rootProject.file("detekt.yml"),
    rootProject.file("detekt-sdk.yml"),
    rootProject.file("detekt_custom_safe_calls_android.yml"),
    rootProject.file("detekt_custom_safe_calls_third_party.yml"),
    rootProject.file("detekt_custom_unsafe_calls.yml")
)

detekt {
    config.setFrom(detektConfigFiles)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
}
