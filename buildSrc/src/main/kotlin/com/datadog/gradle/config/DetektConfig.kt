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
