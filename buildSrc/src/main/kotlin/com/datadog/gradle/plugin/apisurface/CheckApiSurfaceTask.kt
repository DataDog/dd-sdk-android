/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import com.datadog.gradle.utils.execShell
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

open class CheckApiSurfaceTask : DefaultTask() {

    lateinit var surfaceFile: File

    init {
        group = "datadog"
        description = "Check the API surface of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val lines = project.execShell(
            "git", "diff", "--color=never", "HEAD", "--", surfaceFile.absolutePath
        )

        val additions = lines.count { it.matches(Regex("^\\+[^+].*$")) }
        val removals = lines.count { it.matches(Regex("^-[^-].*$")) }

        if (additions > 0 || removals > 0) {

            throw IllegalStateException(
                "Make sure you run the ${ApiSurfacePlugin.TASK_GEN_API_SURFACE} task before you push your PR.\n" +
                    "---------\n" +
                    lines.joinToString("\n") +
                    "\n---------\n"
            )
        }
    }

    @InputFile
    fun getInputFile() = surfaceFile

    // endregion
}
