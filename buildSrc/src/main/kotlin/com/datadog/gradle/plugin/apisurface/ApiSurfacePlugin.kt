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
import java.nio.file.Paths

class ApiSurfacePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val srcDir = File(File(target.projectDir, "src"), "main")
        val genDir = target.layout.buildDirectory
            .dir(Paths.get("generated", "json2kotlin").toString())
            .get()
            .asFile
        val apiDir = File(target.projectDir, "api")
        val kotlinSurfaceFile = File(apiDir, FILE_NAME)
        val javaSurfaceFile = File(apiDir, "${target.name}.api")

        target.tasks
            .register(TASK_GEN_KOTLIN_API_SURFACE, GenerateApiSurfaceTask::class.java) {
                this.srcDirPath = srcDir.absolutePath
                this.genDirPath = genDir.absolutePath
                this.surfaceFile = kotlinSurfaceFile
            }
        target.tasks
            .register(TASK_CHECK_API_SURFACE, CheckApiSurfaceTask::class.java) {
                this.kotlinSurfaceFile = kotlinSurfaceFile
                this.javaSurfaceFile = javaSurfaceFile
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
