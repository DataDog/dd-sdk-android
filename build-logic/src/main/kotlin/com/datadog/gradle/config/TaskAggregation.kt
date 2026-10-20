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
                // Name-filtered live view: it is resolved only when the task graph is built, so it
                // doesn't read the submodule's task container while that is still being populated
                // (e.g. by parallel IDE sync), never configures unrelated tasks, and is simply
                // empty when the submodule doesn't register the task (e.g. lintRelease on JVM modules).
                dependsOn(subProject.tasks.named { it == subModuleTaskName })
            }
        }
        additionalConfiguration()
    }
}
