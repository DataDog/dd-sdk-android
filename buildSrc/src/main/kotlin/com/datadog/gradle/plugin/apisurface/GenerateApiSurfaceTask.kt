/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateApiSurfaceTask : DefaultTask() {
    @get:InputDirectory
    abstract val srcDir: DirectoryProperty

    @get:InputFiles
    abstract val genDir: ConfigurableFileCollection

    @get: OutputFile
    abstract val surfaceFile: RegularFileProperty

    private lateinit var visitor: KotlinFileVisitor

    init {
        group = "datadog"
        description = "Generate the API surface of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        visitor = KotlinFileVisitor()
        visitDirectoryRecursively(srcDir.get().asFile)
        genDir.forEach {
            visitDirectoryRecursively(it)
        }

        surfaceFile.get().asFile.printWriter().use {
            it.print(visitor.description.toString())
        }
    }

    // endregion

    private fun visitDirectoryRecursively(file: File) {
        when {
            !file.exists() -> logger.info("File $file doesn't exist, ignoring")
            file.isDirectory ->
                file.listFiles().orEmpty()
                    .sortedBy { it.absolutePath }
                    .forEach { visitDirectoryRecursively(it) }
            file.isFile -> visitFile(file)
            else -> logger.error("${file.path} is neither file nor directory")
        }
    }

    private fun visitFile(file: File) {
        if (file.canRead()) {
            if (file.extension == EXT_KT) {
                visitor.visitFile(file)
            }
        } else {
            logger.error("${file.path} is not readable")
        }
    }

    companion object {
        const val EXT_KT = "kt"
    }
}
