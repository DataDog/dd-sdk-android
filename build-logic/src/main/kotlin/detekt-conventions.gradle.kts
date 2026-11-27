/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.configureDetekt
import dev.detekt.gradle.Detekt

plugins {
    id("dev.detekt")
}

configureDetekt()

dependencies {
    detektPlugins(project(":tools:detekt"))
}

detekt {
    config.from(
        rootProject.files(
            "detekt-sdk.yml",
            "detekt_custom_safe_calls_android.yml",
            "detekt_custom_safe_calls_third_party.yml",
            "detekt_custom_unsafe_calls.yml"
        )
    )
    ignoredVariants.set(listOf("release"))
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
}
