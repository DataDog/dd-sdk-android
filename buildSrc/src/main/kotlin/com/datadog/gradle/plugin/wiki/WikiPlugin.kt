/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.wiki

import com.datadog.gradle.config.taskConfig
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesExtension
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesPlugin
import com.datadog.gradle.plugin.transdeps.TransitiveDependenciesTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

class WikiPlugin : Plugin<Project> {

    override fun apply(target: Project) {

        val task = target.tasks.register(GEN_TASK_NAME, GenerateWikiTask::class.java) {
            this.srcDir = File(File(File(target.buildDir, "reports"), "javadoc"), target.name)
            this.apiSurface = File(File(target.projectDir, "api"), "apiSurface")
            this.outputDir = File(target.buildDir, "wiki")
            this.projectName = target.name
        }

        target.afterEvaluate {
            val generateWikiTask = task.get()
            tasks.findByName("dokkaGfm")?.let { generateWikiTask.dependsOn(it) }
            tasks.findByName("generateApiSurface")?.let { generateWikiTask.dependsOn(it) }
        }
    }

    companion object {
        const val EXT_NAME = "wiki"
        const val GEN_TASK_NAME = "generateWiki"
    }
}
