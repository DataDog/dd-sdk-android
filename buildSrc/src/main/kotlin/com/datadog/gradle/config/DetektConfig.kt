/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType

fun Project.detektConfig(excludes: List<String> = emptyList()) {

    extensionConfig<DetektExtension> {
        source = files("$projectDir/src/main/kotlin")
        config = files("${project.rootDir}/detekt.yml")

        reports {
            xml {
                enabled = true
                destination = file("build/reports/detekt.xml")
            }
        }
    }

    tasks.withType<Detekt> {
        jvmTarget = "11"

        dependsOn(":tools:detekt:assemble")

        setExcludes(excludes)
    }

    tasks.named("check") {
        dependsOn("detekt")
    }
}
