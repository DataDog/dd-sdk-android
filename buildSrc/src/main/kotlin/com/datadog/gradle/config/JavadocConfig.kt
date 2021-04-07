/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.jetbrains.dokka.gradle.DokkaTask

fun Project.javadocConfig() {

    tasks.withType(DokkaTask::class.java) {
        val toOutputDirectory = file("${buildDir.canonicalPath}/reports/javadoc")
        outputDirectory.set(toOutputDirectory)
        doFirst {
            if (!toOutputDirectory.exists()) {
                toOutputDirectory.mkdirs()
            }
        }
    }
}
