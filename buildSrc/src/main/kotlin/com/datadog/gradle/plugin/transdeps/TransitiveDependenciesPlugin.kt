/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import com.datadog.gradle.config.taskConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class TransitiveDependenciesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.register<GenerateTransitiveDependenciesTask>(TASK_GEN_TRANSITIVE_DEPS) {
            dependenciesFile.set(target.layout.projectDirectory.file(FILE_NAME))
            libsVersionCatalog.set(project.rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
        }

        target.tasks.register<CheckTransitiveDependenciesTask>(TASK_CHECK_TRANSITIVE_DEPS) {
            dependenciesFile.set(target.layout.projectDirectory.file(FILE_NAME))
            dependsOn(TASK_GEN_TRANSITIVE_DEPS)
        }

        target.taskConfig<KotlinCompile> {
            finalizedBy(TASK_GEN_TRANSITIVE_DEPS)
        }
    }

    companion object {

        const val TASK_GEN_TRANSITIVE_DEPS = "generateTransitiveDependenciesList"
        const val TASK_CHECK_TRANSITIVE_DEPS = "checkTransitiveDependenciesList"
        const val FILE_NAME = "transitiveDependencies"
    }
}
