/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import com.datadog.gradle.config.taskConfig
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class ApiSurfacePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val srcDir = File(target.projectDir, "src")
        val surfaceFile = File(target.projectDir, FILE_NAME)

        val generateTask = target.tasks
            .create(TASK_GEN_API_SURFACE, GenerateApiSurfaceTask::class.java)
        generateTask.srcDir = File(srcDir, "main")
        generateTask.surfaceFile = surfaceFile

        val checkTask = target.tasks
            .create(TASK_CHECK_API_SURFACE, CheckApiSurfaceTask::class.java)
        checkTask.surfaceFile = surfaceFile
        checkTask.dependsOn(TASK_GEN_API_SURFACE)

        target.taskConfig<KotlinCompile> {
            finalizedBy(TASK_GEN_API_SURFACE)
        }
    }

    companion object {
        const val TASK_GEN_API_SURFACE = "generateApiSurface"
        const val TASK_CHECK_API_SURFACE = "checkApiSurfaceChanges"
        const val FILE_NAME = "apiSurface"
    }
}
