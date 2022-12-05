/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.wiki

import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class WikiPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.register(GEN_TASK_NAME, GenerateWikiTask::class.java) {
            this.srcDir = File(File(File(target.buildDir, "reports"), "javadoc"), target.name)
            this.apiSurface = File(target.projectDir, "apiSurface")
            this.outputDir = File(target.buildDir, "wiki")
            this.projectName = target.name

            dependsOn("dokkaGfm")
            dependsOn(ApiSurfacePlugin.TASK_GEN_API_SURFACE)
        }
    }

    companion object {
        const val GEN_TASK_NAME = "generateWiki"
    }
}
