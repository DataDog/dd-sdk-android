/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitclone

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class GitCloneDependenciesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions
            .create<GitCloneDependenciesExtension>(EXT_NAME)

        target.tasks
            .register<GitCloneDependenciesTask>(TASK_NAME) {
                this.extension = extension
            }
    }

    companion object {
        const val EXT_NAME = "cloneDependencies"

        const val TASK_NAME = "cloneDependencies"
    }
}
