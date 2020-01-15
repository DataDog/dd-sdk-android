/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

import com.datadog.gradle.plugin.benchmark.model.Benchmark
import com.datadog.gradle.plugin.benchmark.model.BenchmarkResult
import com.google.gson.Gson
import java.io.File

class BenchmarkJsonFileVisitor {

    // region ReviewJsonFileVisitor

    fun visitBenchmarkJsonFile(jsonFile: File, thresholds: Map<String, Long>): Boolean {
        val gson = Gson()
        val benchmarkResult = gson.fromJson(jsonFile.readText(), BenchmarkResult::class.java)

        println(benchmarkResult.context.targetBuild)

        return benchmarkResult.benchmarks.all {
            processBenchmarkResults(it, thresholds)
        }
    }

    // endregion

    // region Internal

    private fun processBenchmarkResults(
        benchmark: Benchmark,
        thresholds: Map<String, Long>
    ): Boolean {
        val testName = benchmark.nameWithoutPrefixes()
        val expectedThreshold = thresholds[testName]
        val median = benchmark.metrics.timeNs.median

        return when {
            expectedThreshold == null -> {
                System.err.println("No benchmark threshold set for test \"$testName\" (from ${benchmark.name})")
                false
            }
            median > expectedThreshold -> {
                System.err.println(
                    "Benchmark test \"$testName\" reported a median time of $median nanos, " +
                        "but threshold is set to $expectedThreshold nanos"
                )
                false
            }
            else -> {
                true
            }
        }
    }

    private fun Benchmark.nameWithoutPrefixes(): String {
        return devicePrefixes.fold(name) { acc, prefix ->
            acc.replace("${prefix}_", "")
        }
    }

    // endregion

    companion object {
        private val devicePrefixes = listOf(
            "EMULATOR", "UNLOCKED", "DEBUGGABLE"
        )
    }
}
