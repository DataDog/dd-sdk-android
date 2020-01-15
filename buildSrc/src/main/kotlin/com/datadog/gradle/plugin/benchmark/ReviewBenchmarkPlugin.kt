/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

import org.gradle.api.Plugin
import org.gradle.api.Project

class ReviewBenchmarkPlugin : Plugin<Project> {

    // region Plugin

    override fun apply(target: Project) {
        val reviewExtension = target.extensions
            .create(EXTENSION_NAME, ReviewBenchmarkExtension::class.java)

        val reviewTask = target.tasks
            .create(TASK_REVIEW_NAME, ReviewBenchmarkResultsTask::class.java)
        reviewTask.buildDir = target.buildDir
        reviewTask.extension = reviewExtension

        reviewTask.dependsOn("connectedCheck")
    }

    // endregion

    companion object {
        const val EXTENSION_NAME = "reviewBenchmark"
        const val TASK_REVIEW_NAME = "reviewBenchmarkResults"
    }
}
