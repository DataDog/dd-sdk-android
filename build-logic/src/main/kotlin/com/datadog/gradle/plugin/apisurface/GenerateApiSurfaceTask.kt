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
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class GenerateApiSurfaceTask : DefaultTask() {

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputDirectory
    abstract val srcDir: DirectoryProperty

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val genDir: ConfigurableFileCollection

    /**
     * Kotlin compiler jars holding the PSI parser. They are deliberately absent from the plugin
     * runtime classpath (see `build-logic/build.gradle.kts`) and only loaded here, inside the
     * worker's isolated classloader.
     */
    @get:Classpath
    abstract val parserClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val surfaceFile: RegularFileProperty

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    init {
        group = "datadog"
        description = "Generate the API surface of the library"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val queue = workerExecutor.classLoaderIsolation {
            classpath.from(parserClasspath)
        }
        queue.submit(GenerateApiSurfaceWorkAction::class.java) {
            srcDir.set(this@GenerateApiSurfaceTask.srcDir)
            genDir.setFrom(this@GenerateApiSurfaceTask.genDir)
            surfaceFile.set(this@GenerateApiSurfaceTask.surfaceFile)
        }
    }

    // endregion
}
