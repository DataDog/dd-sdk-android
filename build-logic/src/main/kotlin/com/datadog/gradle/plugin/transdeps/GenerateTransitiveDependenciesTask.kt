/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.transdeps

import com.datadog.gradle.plugin.aarmetadata.readAarMetadata
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class GenerateTransitiveDependenciesTask : DefaultTask() {

    @get:Input
    var humanReadableSize: Boolean = true

    @get:Input
    var sortByName: Boolean = true

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compileArtifacts: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeArtifacts: ConfigurableFileCollection

    @get:OutputFile
    abstract val dependenciesFile: RegularFileProperty

    private val sizeColumnWidth: Int
        get() = if (humanReadableSize) HUMAN_READABLE_SIZE_WIDTH else RAW_SIZE_WIDTH

    init {
        group = "datadog"
        description = "Generate the list of transitive dependencies of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val outputFile = dependenciesFile.get().asFile
        val compileFiles = compileArtifacts.files
        val runtimeFiles = runtimeArtifacts.files
        val artifacts = (compileFiles + runtimeFiles).toList()
        outputFile.writeText("Dependencies List\n\n")
        outputFile.appendText("${"Dependency".padEnd(PADDING)} | ${"Size".padStart(sizeColumnWidth)} | Classpath\n")
        listDependencies(outputFile, artifacts, compileFiles, runtimeFiles)
        val compileSize = size(compileFiles.sumOf { it.length() }).trim()
        val runtimeSize = size(runtimeFiles.sumOf { it.length() }).trim()
        outputFile.appendText("\nTotal compile dependency artifact size: $compileSize\n")
        outputFile.appendText("Total runtime dependency artifact size: $runtimeSize\n")
    }

    // endregion

    // region Internal

    private fun listDependencies(
        outputFile: File,
        artifacts: List<File>,
        compileFiles: Set<File>,
        runtimeFiles: Set<File>
    ) {
        val sortedArtifacts = if (sortByName) {
            artifacts.sortedBy { it.absolutePath }
        } else {
            artifacts.sortedBy { -it.length() }
        }

        sortedArtifacts.forEach { artifact ->
            val classpath = when {
                artifact in compileFiles && artifact in runtimeFiles -> "both"
                artifact in compileFiles -> "compile"
                else -> "runtime"
            }
            outputFile.appendText(getDependencyFileDescription(artifact, classpath))
        }
    }

    private fun getDependencyFileDescription(artifact: File, classpath: String): String {
        val artifactSize = size(artifact.length()).trim().padStart(sizeColumnWidth)
        val minCompileSdk = if (artifact.extension.equals("aar", ignoreCase = true)) {
            readAarMetadata(artifact)?.getProperty("minCompileSdk")?.trim()?.toIntOrNull()
        } else {
            null
        }
        val requirement = minCompileSdk?.let { " (minCompileSdk=$it)" }.orEmpty()
        val dependency = dependencyCoordinates(artifact) + requirement
        return "${dependency.padEnd(PADDING)} | $artifactSize | $classpath\n"
    }

    private fun dependencyCoordinates(artifactFile: File): String {
        val hash = artifactFile.parentFile
        val version = hash.parentFile
        val artifact = version.parentFile
        val group = artifact.parentFile

        return "${group.name}:${artifact.name}:${version.name}"
    }

    private fun size(size: Long): String {
        if (humanReadableSize) {
            val rawSize = when {
                size >= 2 * MB -> "${size / MB} Mb"
                size >= 2 * KB -> "${size / KB} Kb"
                else -> "$size b "
            }
            return rawSize.padStart(HUMAN_READABLE_SIZE_WIDTH)
        } else {
            return "$size b ".padStart(RAW_SIZE_WIDTH)
        }
    }

    // endregion

    companion object {
        private const val PADDING = 84
        private const val HUMAN_READABLE_SIZE_WIDTH = 8
        private const val RAW_SIZE_WIDTH = 16
        private const val KB = 1024
        private const val MB = 1024 * 1024
    }
}
