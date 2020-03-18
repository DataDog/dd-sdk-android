/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitclone

open class GitCloneDependenciesExtension {

    internal class Dependency(
        var originRepository: String,
        var originSubFolder: String,
        var excludedPrefixes: List<String>
    )

    internal val dependencies: MutableList<Dependency> = mutableListOf()

    fun clone(
        repo: String,
        subFolder: String = "",
        excludedPrefixes: List<String> = emptyList()
    ) {
        dependencies.add(
            Dependency(repo, subFolder, excludedPrefixes)
        )
    }
}
