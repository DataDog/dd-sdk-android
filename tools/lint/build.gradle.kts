/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.taskConfig

plugins {
    // Build
    id("org.jetbrains.kotlin.jvm")
    id("com.android.lint")
    id("datadogBuildConfig")

    // Analysis tools
    id("ktlint")
}

dependencies {
    compileOnly(libs.kotlin)
    compileOnly(libs.androidLintApi)
    compileOnly(libs.androidLintChecks)

    testImplementation(libs.androidLintTests)
    testImplementation(libs.androidLintApi)
    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
}

datadogBuild {
    applyKotlinConfig()
    applyJunitConfig()
}

taskConfig<Jar> {
    manifest {
        attributes("Lint-Registry-v2" to "com.datadog.android.lint.DatadogIssueRegistry")
    }
}
