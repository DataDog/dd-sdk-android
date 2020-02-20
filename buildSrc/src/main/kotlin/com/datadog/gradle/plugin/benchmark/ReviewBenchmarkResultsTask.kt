/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

open class ReviewBenchmarkResultsTask : DefaultTask() {

    internal lateinit var extension: ReviewBenchmarkExtension
    internal lateinit var buildDir: File

    private val visitor = BenchmarkJsonFileVisitor()

    init {
        group = "datadog"
        description = "Review the benchmark results and ensure they are below the specified threshold"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val targetDir = getInputDir()
        if (targetDir.exists() && targetDir.canRead()) {
            targetDir.listFiles()?.forEach {
                processDeviceBenchmarks(it)
            }
        }
    }

    @InputDirectory
    fun getInputDir(): File {
        val outputsDir = File(buildDir, "outputs")
        return File(outputsDir, "connected_android_test_additional_output")
    }

    // endregion

    // region Internal

    private fun processDeviceBenchmarks(deviceDir: File) {
        println("processing benchmark results for device ${deviceDir.name}")
        if (deviceDir.exists() && deviceDir.isDirectory && deviceDir.canRead()) {
            deviceDir.listFiles()?.forEach {
                if (it.isFile && it.canRead() && it.extension == "json") {
                    check(visitor.visitBenchmarkJsonFile(it, extension.thresholds, extension.ignored)) {
                        "One or more benchmark tests in ${it.path} didn't match the expected threshold."
                    }
                }
            }
        }
    }

    // endregion
}
