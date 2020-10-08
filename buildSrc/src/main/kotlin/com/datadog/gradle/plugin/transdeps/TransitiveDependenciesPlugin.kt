/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import com.datadog.gradle.config.taskConfig
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class TransitiveDependenciesPlugin : Plugin<Project> {

    override fun apply(target: Project) {

        val task = target.tasks.create(TASK_NAME, TransitiveDependenciesTask::class.java)
        task.outputFile = File(target.projectDir, FILE_NAME)

        target.taskConfig<KotlinCompile> {
            finalizedBy(TASK_NAME)
        }
    }

    companion object {

        const val TASK_NAME = "listTransitiveDependencies"
        const val FILE_NAME = "transitiveDependencies"
    }
}
