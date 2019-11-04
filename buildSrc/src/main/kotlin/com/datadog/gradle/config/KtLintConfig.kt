package com.datadog.gradle.config

import org.gradle.api.Project
import org.jlleitschuh.gradle.ktlint.KtlintExtension

fun Project.ktLintConfig() {

    extensionConfig<KtlintExtension> {
        debug.set(false)
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(false)
        additionalEditorconfigFile.set(file("${project.rootDir}/script/config/.editorconfig"))
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }

    tasks.named("check") {
        dependsOn("ktlintCheck")
    }
}
