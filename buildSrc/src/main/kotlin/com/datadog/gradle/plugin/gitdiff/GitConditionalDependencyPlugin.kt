/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitdiff

import org.gradle.api.Plugin
import org.gradle.api.Project

class GitConditionalDependencyPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val dependenciesHandler = GitConditionalDependencyLinker(
            GitModifiedPathsProvider(target)
        )
        val conditionalDependencies = listConditionalDependencies(target)
        target.afterEvaluate {
            dependenciesHandler.linkConditionalDependencies(conditionalDependencies)
        }
    }

    private fun listConditionalDependencies(target: Project): Map<Regex, List<Dependency>> {
        return target.tasks.asSequence()
            .filterIsInstance<GitDiffConditionalDependencyTask>()
            .flatMap { task ->
                task.getConditionalDependenciesMap().asSequence()
            }
            .groupBy({ it.first }, { it.second })
    }
}
