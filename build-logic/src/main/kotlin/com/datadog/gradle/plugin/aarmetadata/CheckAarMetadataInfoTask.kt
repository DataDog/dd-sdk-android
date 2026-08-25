/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.aarmetadata

import com.datadog.gradle.plugin.CheckGeneratedFileTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class CheckAarMetadataInfoTask @Inject constructor(
    execOperations: ExecOperations
) : CheckGeneratedFileTask(
    genTaskName = AarMetadataPlugin.TASK_GEN_AAR_METADATA,
    execOperations
) {

    @get:InputFile
    abstract val aarMetadataFile: RegularFileProperty

    init {
        group = "datadog"
        description = "Check the AAR metadata properties of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        verifyGeneratedFileExists(aarMetadataFile.get().asFile)
    }

    // endregion
}
