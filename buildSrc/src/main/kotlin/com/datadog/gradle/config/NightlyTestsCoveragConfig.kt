/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.tasks.Exec

fun Project.nightlyTestsCoverageConfig(threshold: Float) {
    tasks.register("checkNightlyTestsCoverage", Exec::class.java) {
        this.group = "datadog"
        val generateApiTasks = allprojects.mapNotNull { it.tasks.findByName("generateApiSurface") }
        setDependsOn(generateApiTasks)
        setCommandLine(
            "python3",
            "instrumentation-tools/nightly_tests_code_coverage.py",
            "-t $threshold"
        )
    }
}
