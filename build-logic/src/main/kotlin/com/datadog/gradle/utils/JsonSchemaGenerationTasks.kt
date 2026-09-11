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
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
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
        projectDirPath.set(layout.projectDirectory.asFile.path)
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

    val rootTask = rootProject.tasks.maybeCreate(GENERATE_ALL_JSON_MODELS_TASK_NAME)

    rootTask.dependsOn(task)

    val androidComponents = extensions.findByType<LibraryAndroidComponentsExtension>()
        ?: error(
            "Project $path applies the JSON models generation without the Android library plugin," +
                " the generated sources cannot be wired to any variant."
        )
    androidComponents.onVariants { variant ->
        // Registered on BOTH source sets on purpose, they are read by different consumers:
        //  - `java`   : KSP resolves the generated models from it (Kotlin-only registration makes
        //               kspDebugKotlin fail with "Error type '<ERROR TYPE: ViewEvent>'").
        //  - `kotlin` : detekt's Android integration collects only this one, and without it every
        //               type touching a generated model degrades to `UNKNOWN`, which makes
        //               UnsafeThirdPartyFunctionCall fire across the whole call graph.
        variant.sources.java?.addGeneratedSourceDirectory(
            task,
            GenerateJsonSchemaTask::destinationGenDirectory
        )
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            task,
            GenerateJsonSchemaTask::destinationGenDirectory
        )
    }
}
