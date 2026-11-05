/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import dev.detekt.gradle.Detekt

plugins {
    id("dev.detekt")
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    // Keep release analysis when a module has release-specific sources, including tests.
    val releaseSources = fileTree("src") {
        include("release*/**/*.kt", "*Release*/**/*.kt", "release*/**/*.kts", "*Release*/**/*.kts")
    }
    if (releaseSources.isEmpty) {
        ignoredBuildTypes.add("release")
    }
}

tasks.withType<Detekt>().configureEach {
    reports {
        sarif.required = false
        html.required = false
        markdown.required = false
        checkstyle.required = false
    }
}

// Use the compilation tasks so rules requiring type resolution also run on tests and fixtures.
tasks.named<Detekt>("detekt") {
    isEnabled = false
    dependsOn(tasks.named { it in setOf("detektMain", "detektTest", "detektTestFixtures") })
}
