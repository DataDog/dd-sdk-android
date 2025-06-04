/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import com.datadog.gradle.plugin.CheckGeneratedFileTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

open class CheckApiSurfaceTask : CheckGeneratedFileTask(
    genTaskName = ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE
) {

    @InputFile
    lateinit var kotlinSurfaceFile: File

    @InputFiles
    lateinit var javaSurfaceFile: File

    init {
        group = "datadog"
        description = "Check the API surface of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        verifyGeneratedFileExists(kotlinSurfaceFile)
        if (javaSurfaceFile.exists()) {
            verifyGeneratedFileExists(javaSurfaceFile)
        }
    }

    @InputFile
    fun getInputFile() = kotlinSurfaceFile

    // endregion
}
