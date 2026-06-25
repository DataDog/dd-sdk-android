/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.Task

fun Project.registerSubModuleAggregationTask(
    taskName: String,
    subModuleTaskName: String,
    subModulePathPrefix: String = ":",
    subModuleNamePrefix: String = "dd-sdk-android-",
    exceptions: Set<String> = emptySet(),
    additionalConfiguration: Task.() -> Unit = {}
) {
    tasks.register(taskName) {
        project.subprojects.forEach { subProject ->
            if (!exceptions.contains(subProject.name) &&
                subProject.name.startsWith(subModuleNamePrefix) &&
                subProject.path.startsWith(subModulePathPrefix)
            ) {
                // Lazily depend on the task only when the submodule registers it: `tasks.names`
                // reads registered task names without creating any task, and `named()` returns a
                // lazy provider, so unrelated tasks are never configured. The membership check
                // matters because not every submodule registers every aggregated task (e.g.
                // printDetektClasspath only exists where detekt is applied).
                if (subModuleTaskName in subProject.tasks.names) {
                    dependsOn(subProject.tasks.named(subModuleTaskName))
                }
            }
        }
        additionalConfiguration()
    }
}
