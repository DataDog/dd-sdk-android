/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType

internal fun Project.configureDetekt() {
    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.file("detekt.yml"))
        basePath.set(rootProject.layout.projectDirectory)
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            sarif.required.set(false)
            html.required.set(false)
            markdown.required.set(false)
            checkstyle.required.set(false)
        }
    }

    // The aggregate tasks use the compile-time classpath for type-aware analysis.
    tasks.named<Detekt>("detekt") {
        isEnabled = false
        dependsOn(tasks.named { it == "detektMain" || it == "detektTest" })
    }
}
