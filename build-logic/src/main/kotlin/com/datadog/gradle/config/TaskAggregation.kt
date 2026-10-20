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
                // The provider defers the name check until the task graph is built, so it doesn't
                // read the submodule's task container while that is still being populated (e.g. by
                // parallel IDE sync). `tasks.names` and `named()` don't create unrelated tasks,
                // unlike iterating `tasks.named { }`. The check matters because not every submodule
                // registers every aggregated task (e.g. lintRelease on JVM modules).
                dependsOn(
                    project.provider {
                        if (subModuleTaskName in subProject.tasks.names) {
                            listOf(subProject.tasks.named(subModuleTaskName))
                        } else {
                            emptyList()
                        }
                    }
                )
            }
        }
        additionalConfiguration()
    }
}
