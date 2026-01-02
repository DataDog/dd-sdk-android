/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.utils

import com.datadog.gradle.plugin.apisurface.ApiSurfacePlugin
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesExtension
import com.datadog.gradle.plugin.gitclone.GitCloneDependenciesTask
import com.datadog.gradle.plugin.jsonschema.GenerateJsonSchemaTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private const val RUM_EVENTS_FORMAT_REPO = "https://github.com/DataDog/rum-events-format.git"
private const val CLONE_ALL_RUM_SCHEMAS_TASK_NAME = "cloneAllRumSchemas"
private const val GENERATE_ALL_JSON_MODELS_TASK_NAME = "generateAllJsonModels"

fun Project.createRumSchemaCloningTask(
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
        action()
    }

    val rootTask = rootProject.tasks.maybeCreate(GENERATE_ALL_JSON_MODELS_TASK_NAME)

    rootTask.dependsOn(task)

    afterEvaluate {
        tasks.findByName(ApiSurfacePlugin.TASK_GEN_KOTLIN_API_SURFACE)
            ?.dependsOn(taskName)
        tasks.withType(KotlinCompile::class.java).configureEach {
            dependsOn(taskName)
        }
    }
}
