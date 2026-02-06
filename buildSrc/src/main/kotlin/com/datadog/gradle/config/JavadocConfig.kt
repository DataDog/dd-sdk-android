/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.dokka.gradle.DokkaExtension
import java.nio.file.Paths

fun Project.javadocConfig() {
    extensions.configure<DokkaExtension>() {
        dokkaPublications.named("javadoc") {
            val toOutputDirectory = layout.buildDirectory
                .dir(Paths.get("reports", "javadoc").toString())
            outputDirectory.set(toOutputDirectory)
        }
    }
}
