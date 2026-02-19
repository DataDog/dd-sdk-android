/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.gradle.plugin.logsconfig

import com.android.build.gradle.LibraryExtension
import com.datadog.gradle.config.taskConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.nio.file.Paths

class LogsConfigPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create<LogsConfigExtension>(EXTENSION_NAME)

        val logsDir = File(target.projectDir, LOGS_DIR)
        if (!logsDir.isDirectory) {
            target.logger.info("No $LOGS_DIR directory found in ${target.projectDir}, skipping logs config generation")
            return
        }

        val genDir = target.layout.buildDirectory
            .dir(Paths.get(GEN_DIR, "main", "kotlin").toString())

        val derivedClassName = LogsConfigCodeGenerator.moduleNameToClassName(target.name)

        val generateTask = target.tasks.register<GenerateLogsConfigTask>(TASK_NAME) {
            inputDirectory.set(logsDir)
            targetPackageName.convention(extension.packageName)
            loggerClassName.convention(derivedClassName)
            outputDirectory.set(genDir)
        }

        target.taskConfig<KotlinCompile> {
            dependsOn(generateTask)
        }

        target.pluginManager.withPlugin("com.android.library") {
            target.extensions.getByType(LibraryExtension::class.java).apply {
                sourceSets.named("main") {
                    java.srcDir(genDir)
                }
            }
        }
    }

    companion object {
        const val EXTENSION_NAME = "logsConfig"
        const val LOGS_DIR = "src/main/logs"
        const val TASK_NAME = "generateLogsConfig"
        private const val GEN_DIR = "generated/logsConfig"
    }
}
