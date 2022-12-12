/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import com.datadog.gradle.config.taskConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

class ApiSurfacePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val srcDir = File(File(target.projectDir, "src"), "main")
        val genDir = File(File(target.buildDir, "generated"), "json2kotlin")
        val surfaceFile = File(target.projectDir, FILE_NAME)
        genDir.mkdirs()

        target.tasks
            .register(TASK_GEN_API_SURFACE, GenerateApiSurfaceTask::class.java) {
                this.srcDir = srcDir
                this.genDir = genDir
                this.surfaceFile = surfaceFile
            }
        target.tasks
            .register(TASK_CHECK_API_SURFACE, CheckApiSurfaceTask::class.java) {
                this.surfaceFile = surfaceFile
                dependsOn(TASK_GEN_API_SURFACE)
            }

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
