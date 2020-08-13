/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

open class ReviewBenchmarkResultsTask : DefaultTask() {

    @get: Input
    internal lateinit var extension: ReviewBenchmarkExtension
    @get: InputDirectory
    internal lateinit var buildDir: File
    @get: Input
    val visitor = BenchmarkJsonFileVisitor()

    init {
        group = "datadog"
        description =
            "Review the benchmark results and ensure they are below the specified threshold"
    }

    // region Task

    @TaskAction
    fun applyTask() {
        val targetDir = getInputDir()
        if (targetDir.exists() && targetDir.canRead()) {
            targetDir.listFiles()?.forEach {
                processBenchmarksPerDevice(it)
            }
        }
    }

    @InputDirectory
    fun getInputDir(): File {
        val outputsDir = File(buildDir, "outputs")
        return File(
            outputsDir,
            "connected_android_test_additional_output/debugAndroidTest/connected"
        )
    }

    // endregion

    // region Internal

    private fun processBenchmarksPerDevice(
        deviceDir: File
    ) {
        println("processing benchmark results for device ${deviceDir.name}")
        if (deviceDir.exists() && deviceDir.isDirectory && deviceDir.canRead()) {
            val benchmarksResults = mutableMapOf<String, Long>()
            deviceDir.listFiles()?.forEach {
                if (it.isFile && it.canRead() && it.extension == "json") {
                    visitor.visitBenchmarkJsonFile(it, benchmarksResults)
                }
            }

            val strategies = extension.benchmarkStrategies

            // verify that all the benchmarks have at least one strategy and
            // then verify the strategy
            benchmarksResults.forEach {
                val strategy = strategies[it.key]
                println(
                    "Reviewing benchmark result: ${it.key} with" +
                        " strategy: ${strategy?.javaClass?.simpleName}"
                )
                checkNotNull(strategy) {
                    System.err.println("No benchmarking strategy added for test \"${it.key}\"")
                }

                check(strategy.verify(benchmarksResults))
            }
        }
    }

    // endregion
}
