/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import com.android.build.gradle.tasks.SourceJarTask
import com.datadog.gradle.config.taskConfig
import com.datadog.gradle.plugin.apisurface.GenerateApiSurfaceTask
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesExtension
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask
import com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.nio.file.Paths

private const val RUM_EVENTS_FORMAT_REPO = "https://github.com/DataDog/rum-events-format.git"
private const val CLONE_ALL_RUM_SCHEMAS_TASK_NAME = "cloneAllRumSchemas"
private const val GENERATE_ALL_JSON_MODELS_TASK_NAME = "generateAllJsonModels"

fun Project.createRumSchemaCloneTask(
    taskName: String,
    action: GitCloneDependenciesExtension.() -> Unit
) {
    val task = tasks.register<GitCloneDependenciesTask>(taskName) {
        extension.apply(action)
    }

    val rootTask = rootProject.tasks.maybeCreate(CLONE_ALL_RUM_SCHEMAS_TASK_NAME)

    rootTask.dependsOn(task)
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
        ignoredFiles.convention(emptyList())
        inputDirPath.convention("")
        targetPackageName.convention("")

        action()

        val rootPath = Paths.get("generated", "json2kotlin", "main", "kotlin")
        val rootGenDirectory = layout.buildDirectory
            .dir(rootPath.toString())
            .get()
            .asFile
        destinationGenDirectoryPath.set(rootGenDirectory.absolutePath)
        val outputPackageDir = File(
            rootGenDirectory,
            targetPackageName.get().replace(".", File.separator)
        ).apply {
            if (!exists()) mkdirs()
        }
        destinationPackageDirectory.set(outputPackageDir)
    }

    val rootTask = rootProject.tasks.maybeCreate(GENERATE_ALL_JSON_MODELS_TASK_NAME)

    rootTask.dependsOn(task)

    taskConfig<GenerateApiSurfaceTask> {
        dependsOn(task)
    }

    taskConfig<KotlinCompile> {
        dependsOn(task)
    }

    // need to add an explicit dependency, otherwise there is an error during publishing
    // Task 'sourceReleaseJar' uses this output of task
    // this task without declaring an explicit or implicit dependency
    taskConfig<SourceJarTask> {
        dependsOn(task)
    }
}
