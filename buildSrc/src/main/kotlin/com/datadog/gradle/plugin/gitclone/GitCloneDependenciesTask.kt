/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.gitclone

import com.datadog.gradle.utils.execShell
import java.io.File
import java.nio.file.Files.createTempDirectory
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class GitCloneDependenciesTask : DefaultTask() {

    @get: Input
    var extension: GitCloneDependenciesExtension =
        GitCloneDependenciesExtension()

    init {
        group = "datadog"
        description = "Clones the source code of a dependency into this module"
        outputs.upToDateWhen { false }
    }

    // region Task

    @TaskAction
    fun applyTask() {
        extension.dependencies.forEach {
            cloneDependency(it)
        }
    }

    // endregion

    // region Internal

    private fun cloneDependency(
        dependency: GitCloneDependenciesExtension.Dependency
    ) {
        val target = createTempDirectory(null).toFile()
        cloneRepository(dependency, target)

        val copyFrom = if (dependency.originSubFolder.isEmpty()) {
            target
        } else {
            File("${target.absolutePath}${File.separator}${dependency.originSubFolder}")
        }
        val copyTo = File(project.projectDir.path + File.separator + dependency.destinationFolder)
        copySources(copyFrom, copyTo, dependency.excludedPrefixes)

        deleteClone(target)
    }

    private fun cloneRepository(
        dependency: GitCloneDependenciesExtension.Dependency,
        target: File
    ) {
        println(" --- Cloning ${dependency.originRepository} into ${target.absolutePath}")
        project.execShell(
            "git",
            "clone",
            "--branch",
            dependency.originRef,
            "--depth",
            "1",
            dependency.originRepository,
            target.absolutePath
        )
    }

    private fun copySources(
        src: File,
        dest: File,
        excludedPrefixes: List<String>
    ) {
        val srcPath = "${src.absolutePath}${File.separator}"
        val destPath = "${dest.absolutePath}${File.separator}"
        src.walkBottomUp()
            .filter { it.isFile }
            .forEach { copyFile(it, srcPath, destPath, excludedPrefixes) }
    }

    private fun copyFile(
        child: File,
        srcPath: String,
        destPath: String,
        excludedPrefixes: List<String>
    ) {
        val copyFromPath = child.absolutePath
        val relativePath = copyFromPath.substring(srcPath.length)

        if (excludedPrefixes.any { relativePath.startsWith(it) }) {
            return
        }

        val copyIntoPath = "$destPath$relativePath"
        val destFile = File(copyIntoPath)
        val parentFile = destFile.parentFile

        if (parentFile.exists() || parentFile.mkdirs()) {
            copyFileWithCorrections(child, destFile)
        } else {
            System.err.println("  x Unable to copy file $copyFromPath into $copyIntoPath")
        }
    }

    private fun copyFileWithCorrections(
        child: File,
        destFile: File
    ) {
        child.inputStream().reader(Charsets.UTF_8).use { reader ->
            destFile.outputStream().writer(Charsets.UTF_8).use { writer ->
                reader.forEachLine {
                    if (keepLine(it)) {
                        writer.write(it)
                        writer.write("\n")
                    }
                }
            }
        }
    }

    private fun keepLine(line: String): Boolean {
        return when {
            LOG_LINE_REGEX.matches(line) -> false
            SLF4J_REGEX.matches(line) -> false
            LOMBOK_IMPORTS_REGEX.matches(line) -> false
            else -> true
        }
    }

    private fun deleteClone(target: File) {
        println(" --- Deleting temp folder ${target.absolutePath}")
        project.execShell("rm", "-r", target.absolutePath)
        println(" --- Deleted")
    }

    // endregion

    companion object {
        private val LOG_LINE_REGEX = Regex("\\s+log\\.(debug|info|warn|error)\\(.*\\);\\s*")
        private val SLF4J_REGEX = Regex("\\s*@Slf4j\\s*")

        private val LOMBOK_IMPORTS_REGEX = Regex("import lombok\\..*;\\s*")
    }
}
