/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

open class TransitiveDependenciesTask : DefaultTask() {

    @get:Input
    var humanReadableSize: Boolean = true

    @get:Input
    var sortByName: Boolean = true

    @get: OutputFile
    lateinit var outputFile: File

    init {
        group = "datadog"
        description = "Generate the list of transitive dependencies of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        outputFile.writeText("Dependencies List\n\n")
        val implementation = project.configurations.getByName("releaseCompileClasspath")
        listConfigurationDependencies(implementation)
    }

    // endregion

    // region Internal

    private fun listConfigurationDependencies(configuration: Configuration) {
        check(configuration.isCanBeResolved) { "$configuration cannot be resolved" }

        val sortedArtifacts = if (sortByName) {
            configuration.files {
                // ProjectDependency (i.e. local modules) don't have a file associated
                it !is ProjectDependency
            }.sortedBy { it.absolutePath }
        } else {
            configuration.sortedBy { -it.length() }
        }

        var sum = 0L
        sortedArtifacts.forEach {
            sum += it.length()
            outputFile.appendText(getDependencyFileDescription(it))
        }

        outputFile.appendText("\n${TOTAL.padEnd(PADDING)}:${size(sum)}\n\n")
    }

    private fun getDependencyFileDescription(it: File): String {
        val hash = it.parentFile
        val version = hash.parentFile
        val artifact = version.parentFile
        val group = artifact.parentFile

        val title = "${group.name}:${artifact.name}:${version.name}"

        return "${title.padEnd(PADDING)}:${size(it.length())}\n"
    }

    private fun size(size: Long): String {
        if (humanReadableSize) {
            val rawSize = when {
                size >= 2 * MB -> "${size / MB} Mb"
                size >= 2 * KB -> "${size / KB} Kb"
                else -> "$size b "
            }
            return rawSize.padStart(8)
        } else {
            return "$size b ".padStart(16)
        }
    }

    // endregion

    companion object {
        private const val PADDING = 64
        private const val KB = 1024
        private const val MB = 1024 * 1024

        private const val TOTAL = "Total transitive dependencies size"
    }
}
