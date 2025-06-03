/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import com.datadog.gradle.plugin.CheckGeneratedFileTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

open class CheckTransitiveDependenciesTask : CheckGeneratedFileTask(
    genTaskName = TransitiveDependenciesPlugin.TASK_GEN_TRANSITIVE_DEPS
) {

    @InputFile
    lateinit var dependenciesFile: File

    init {
        group = "datadog"
        description = "Check the transitive dependencies of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        verifyGeneratedFileExists(dependenciesFile)
    }

    @InputFile
    fun getInputFile() = dependenciesFile

    // endregion
}
