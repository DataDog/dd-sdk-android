/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

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
            exclude("**/com/datadog/android/rum/internal/domain/model/**")
            include("**/kotlin/**")
        }
    }

    tasks.named("check") {
        dependsOn("ktlintCheck")
    }
}
