/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitdiff

class GitConditionalDependencyLinker(
    private val gitModifiedPathsProvider: GitModifiedPathsProvider
) {

    fun linkConditionalDependencies(
        dependencies: Map<Regex, List<Dependency>>
    ) {
        val diffPaths = gitModifiedPathsProvider.getModifiedPaths()
        dependencies.forEach { (regex, dependencies) ->
            if (diffPaths.any { it.matches(regex) }) {
                linkDependencies(dependencies)
            }
        }
    }

    private fun linkDependencies(dependencies: List<Dependency>) {
        dependencies.forEach {
            it.task.dependsOn(it.dependsOn)
        }
    }
}
