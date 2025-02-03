/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaTask
import java.nio.file.Paths

fun Project.javadocConfig() {
    tasks.withType(DokkaTask::class.java).configureEach {
        val toOutputDirectory = layout.buildDirectory
            .dir(Paths.get("reports", "javadoc").toString())
        outputDirectory.set(toOutputDirectory)
        doFirst {
            toOutputDirectory.get().asFile.let {
                if (!it.exists()) {
                    it.mkdirs()
                }
            }
        }
    }
}
