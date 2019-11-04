package com.datadog.gradle.config

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.Project

fun Project.dependencyUpdateConfig() {

    taskConfig<DependencyUpdatesTask> {
        revision = "release"
    }
}
