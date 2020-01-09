/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitdiff

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.register

/**
 * This task does nothing. It simply holds a list of conditional dependencies
 * based on modified/added/deleted files in the repo.
 */
open class GitDiffConditionalDependencyTask : DefaultTask() {

    private val dependencies: MutableMap<String, MutableList<String>> = mutableMapOf()

    // region Task

    @Input
    fun getConditionalDependenciesInput() : Map<String, List<String>> {
        return dependencies
    }

    // endregion

    fun dependsOnDiff(pattern: String, vararg tasks: String) {
        val list = dependencies[pattern] ?: mutableListOf<String>()
            .apply { dependencies[pattern] = this }
        tasks.forEach {
            list.add(it)
        }
    }

    fun getConditionalDependenciesMap() : List<Pair<Regex, Dependency>> {
        return dependencies.map {
            Regex(it.key) to Dependency(this, it.value)
        }
    }
}

fun Project.gitDiffTask(name: String, configuration: Action<GitDiffConditionalDependencyTask>) {
    tasks.register(name, GitDiffConditionalDependencyTask::class, configuration)
}