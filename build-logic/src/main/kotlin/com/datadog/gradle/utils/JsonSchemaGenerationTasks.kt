/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesExtension
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask
import com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.nio.file.Paths

private const val RUM_EVENTS_FORMAT_REPO = "https://github.com/DataDog/rum-events-format.git"

/**
 * Per-module lifecycle task depending on every RUM schema clone task of the module, aggregated by
 * the root `cloneAllRumSchemas` task.
 */
const val CLONE_RUM_EVENTS_FORMAT_SCHEMAS_TASK_NAME = "cloneRumEventsFormatSchemas"

/**
 * Per-module lifecycle task depending on every JSON models generation task of the module,
 * aggregated by the root `generateAllJsonModels` task.
 */
const val GENERATE_JSON_MODELS_TASK_NAME = "generateJsonModels"

fun Project.createRumEventsFormatCloneTask(
    taskName: String,
    action: GitCloneDependenciesExtension.() -> Unit
) {
    val task = tasks.register<GitCloneDependenciesTask>(taskName) {
        extension.apply(action)
        projectDirPath.set(layout.projectDirectory.asFile.path)
    }

    lifecycleTask(
        CLONE_RUM_EVENTS_FORMAT_SCHEMAS_TASK_NAME,
        "Clones every rum-events-format schema used by this module"
    ).configure { dependsOn(task) }
}

fun GitCloneDependenciesExtension.cloneRumEventsFormat(
    project: Project,
    subFolder: String,
    destinationFolder: String,
    excludedPrefixes: List<String> = emptyList()
) {
    val repositoryRef = project.findProperty("dd.rum.schema.ref") as? String ?: "master"

    clone(
        repo = RUM_EVENTS_FORMAT_REPO,
        subFolder = subFolder,
        destinationFolder = destinationFolder,
        ref = repositoryRef,
        excludedPrefixes = excludedPrefixes
    )
}

fun Project.createJsonModelsGenerationTask(
    taskName: String,
    action: GenerateJsonSchemaTask.() -> Unit
) {
    val task = tasks.register<GenerateJsonSchemaTask>(taskName) {
        inputNameMapping.convention(emptyMap())
        schemasWithoutConstructorDefaults.convention(emptyList())
        ignoredFiles.convention(emptyList())
        inputDirPath.convention("")
        targetPackageName.convention("")
        extraInputWatchDir.convention(null)

        action()

        destinationGenDirectory.set(
            layout.buildDirectory.dir(Paths.get("generated", taskName).toString())
        )
        inputDir.set(layout.projectDirectory.dir(inputDirPath))
        inputFiles.from(
            inputDir.map {
                it.asFileTree.matching { include { it.file.isFile && it.file.extension == "json" } }
            }
        )
    }

    lifecycleTask(
        GENERATE_JSON_MODELS_TASK_NAME,
        "Generates every model of this module from its JSON schemas"
    ).configure { dependsOn(task) }

    val androidComponents = extensions.findByType<LibraryAndroidComponentsExtension>()
        ?: error(
            "Project $path applies the JSON models generation without the Android library plugin," +
                " the generated sources cannot be wired to any variant."
        )
    androidComponents.onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            task,
            GenerateJsonSchemaTask::destinationGenDirectory
        )
    }
}

private fun Project.lifecycleTask(name: String, description: String): TaskProvider<Task> {
    if (name in tasks.names) return tasks.named(name)
    return tasks.register(name) {
        group = "datadog"
        this.description = description
    }
}
