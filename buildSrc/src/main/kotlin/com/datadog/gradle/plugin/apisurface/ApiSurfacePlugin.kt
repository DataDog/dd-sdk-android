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
        val srcDir = File(File(target.projectDir, "src"), "main")
        val genDir = File(File(target.buildDir, "generated"), "json2kotlin")
        val apiDir = File(target.projectDir, "api")
        val surfaceFile = File(apiDir, FILE_NAME)

        target.tasks
            .register(TASK_GEN_KOTLIN_API_SURFACE, GenerateApiSurfaceTask::class.java) {
                this.srcDirPath = srcDir.absolutePath
                this.genDirPath = genDir.absolutePath
                this.surfaceFile = surfaceFile
            }
        target.tasks
            .register(TASK_CHECK_API_SURFACE, CheckApiSurfaceTask::class.java) {
                this.surfaceFile = surfaceFile
                dependsOn(TASK_GEN_KOTLIN_API_SURFACE)
            }

        target.taskConfig<KotlinCompile> {
            // Java API generation task does a clean-up of all files in the output
            // folder, so let it run first
            if (target.plugins.hasPlugin(GEN_JAVA_API_LAYOUT_PLUGIN)) {
                finalizedBy(TASK_GEN_JAVA_API_SURFACE)
            }
            finalizedBy(TASK_GEN_KOTLIN_API_SURFACE)
        }
    }

    companion object {
        const val TASK_GEN_KOTLIN_API_SURFACE = "generateApiSurface"
        const val TASK_GEN_JAVA_API_SURFACE = "apiDump"
        const val TASK_CHECK_API_SURFACE = "checkApiSurfaceChanges"
        const val FILE_NAME = "apiSurface"
        const val GEN_JAVA_API_LAYOUT_PLUGIN = "binary-compatibility-validator"
    }
}
