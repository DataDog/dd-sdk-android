/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import com.datadog.gradle.config.taskConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

class TransitiveDependenciesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val generateTransitiveDependenciesTask = target.tasks
            .register<GenerateTransitiveDependenciesTask>(TASK_GEN_TRANSITIVE_DEPS) {
                dependenciesFile.set(target.layout.projectDirectory.file(FILE_NAME))
                compileArtifacts.from(target.externalArtifacts("releaseCompileClasspath"))
                runtimeArtifacts.from(target.externalArtifacts("releaseRuntimeClasspath"))
            }

        target.tasks.register<CheckTransitiveDependenciesTask>(TASK_CHECK_TRANSITIVE_DEPS) {
            dependenciesFile.set(generateTransitiveDependenciesTask.flatMap { it.dependenciesFile })
        }

        target.taskConfig<KotlinCompilationTask<KotlinJvmCompilerOptions>> {
            finalizedBy(TASK_GEN_TRANSITIVE_DEPS)
        }
    }

    private fun Project.externalArtifacts(configurationName: String): Provider<FileCollection> {
        return configurations.named(configurationName).map { configuration ->
            configuration.externalDependencyArtifacts()
        }
    }

    companion object {

        const val TASK_GEN_TRANSITIVE_DEPS = "generateTransitiveDependenciesList"
        const val TASK_CHECK_TRANSITIVE_DEPS = "checkTransitiveDependenciesList"
        const val FILE_NAME = "transitiveDependencies"
    }
}

internal fun Configuration.externalDependencyArtifacts(): FileCollection {
    val components = mutableSetOf<ComponentIdentifier>()
    val pending = ArrayDeque<ResolvedComponentResult>()
    pending.add(incoming.resolutionResult.rootComponent.get())
    while (pending.isNotEmpty()) {
        pending.removeFirst().dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
            val selected = dependency.selected
            if (!dependency.isConstraint && selected.id !is ProjectComponentIdentifier && components.add(selected.id)) {
                pending.add(selected)
            }
        }
    }
    return incoming.artifactView {
        componentFilter { componentId ->
            componentId in components
        }
    }.files
}
