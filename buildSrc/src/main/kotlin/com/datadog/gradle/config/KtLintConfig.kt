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
        version.set("0.44.0")
        debug.set(false)
        android.set(true)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        additionalEditorconfigFile.set(file("${project.rootDir}/script/config/.editorconfig"))
        filter {
            exclude("**/generated/**")
            exclude("**/com/datadog/android/rum/model/**")
            exclude("**/com/datadog/android/telemetry/model/**")
            exclude("**/com/datadog/android/core/model/**")
            exclude("**/com/datadog/android/tracing/model/**")
            exclude("**/com/datadog/android/log/model/**")
            exclude("**/com/datadog/android/sessionreplay/model/**")
            include("**/kotlin/**")
        }
    }

    tasks.named("check") {
        dependsOn("ktlintCheck")
    }
}
