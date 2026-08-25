/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.apisurface

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Walks the source directories and writes the API surface description.
 *
 * Runs in a worker with an isolated classloader, which is where the Kotlin PSI classes used by
 * [KotlinFileVisitor] come from.
 */
abstract class GenerateApiSurfaceWorkAction : WorkAction<GenerateApiSurfaceWorkAction.Params> {

    interface Params : WorkParameters {
        val srcDir: DirectoryProperty
        val genDir: ConfigurableFileCollection
        val surfaceFile: RegularFileProperty
    }

    private val visitor = KotlinFileVisitor()

    override fun execute() {
        visitDirectoryRecursively(parameters.srcDir.get().asFile)
        parameters.genDir.forEach {
            visitDirectoryRecursively(it)
        }

        parameters.surfaceFile.get().asFile.printWriter().use {
            it.print(visitor.description.toString())
        }
    }

    private fun visitDirectoryRecursively(file: File) {
        when {
            !file.exists() -> LOGGER.info("File {} doesn't exist, ignoring", file)
            file.isDirectory ->
                file.listFiles().orEmpty()
                    .sortedBy { it.absolutePath }
                    .forEach { visitDirectoryRecursively(it) }

            file.isFile -> visitFile(file)
            else -> LOGGER.error("{} is neither file nor directory", file.path)
        }
    }

    private fun visitFile(file: File) {
        if (file.canRead()) {
            if (file.extension == EXT_KT) {
                visitor.visitFile(file)
            }
        } else {
            LOGGER.error("{} is not readable", file.path)
        }
    }

    companion object {
        const val EXT_KT = "kt"
        private val LOGGER = LoggerFactory.getLogger(GenerateApiSurfaceWorkAction::class.java)
    }
}
