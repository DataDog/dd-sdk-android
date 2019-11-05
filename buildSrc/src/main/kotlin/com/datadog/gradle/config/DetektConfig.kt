/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.gradle.config

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project

fun Project.detektConfig() {

    extensionConfig<DetektExtension> {
        version = "1.0.1"

        input = files("$projectDir/src/main/kotlin")
        config = files("${project.rootDir}/detekt.yml")
        reports {
            xml {
                enabled = true
                destination = file("build/reports/detekt.xml")
            }
        }
    }

    tasks.named("check") {
        dependsOn("detekt")
    }
}
