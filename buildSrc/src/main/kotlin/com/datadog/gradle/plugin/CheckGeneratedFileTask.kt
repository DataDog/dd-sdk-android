/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin

import com.datadog.gradle.utils.execShell
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.io.File

abstract class CheckGeneratedFileTask(
    @Internal val genTaskName: String,
    private val execOperations: ExecOperations
) : DefaultTask() {

    // region Task

    fun verifyGeneratedFileExists(targetFile: File) {
        val lines = execOperations.execShell(
            "git",
            "diff",
            "--color=never",
            "HEAD",
            "--",
            targetFile.absolutePath
        )

        val additions = lines.filter { it.matches(Regex("^\\+[^+].*$")) }
        val removals = lines.filter { it.matches(Regex("^-[^-].*$")) }

        if (additions.isNotEmpty() || removals.isNotEmpty()) {
            error(
                "Make sure you run the $genTaskName task  before you push your PR.\n" +
                    additions.joinToString("\n") +
                    "\n" +
                    removals.joinToString("\n")
            )
        }
    }

    // endregion
}
