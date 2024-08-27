/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import com.datadog.gradle.utils.execShell
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

open class CheckTransitiveDependenciesTask : DefaultTask() {

    @InputFile
    lateinit var dependenciesFile: File

    init {
        group = "datadog"
        description = "Check the transitive dependencies of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val lines = project.execShell(
            "git",
            "diff",
            "--color=never",
            "HEAD",
            "--",
            dependenciesFile.absolutePath
        )

        val additions = lines.filter { it.matches(Regex("^\\+[^+].*$")) }
        val removals = lines.filter { it.matches(Regex("^-[^-].*$")) }

        if (additions.isNotEmpty() || removals.isNotEmpty()) {
            error(
                "Make sure you run the ${TransitiveDependenciesPlugin.TASK_GEN_TRANSITIVE_DEPS} task" +
                    " before you push your PR.\n" +
                    additions.joinToString("\n") +
                    "\n" +
                    removals.joinToString("\n")
            )
        }
    }

    @InputFile
    fun getInputFile() = dependenciesFile

    // endregion
}
