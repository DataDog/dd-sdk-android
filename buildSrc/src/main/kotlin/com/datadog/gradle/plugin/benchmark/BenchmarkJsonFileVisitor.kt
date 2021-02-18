/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.benchmark

import com.datadog.gradle.plugin.benchmark.model.Benchmark
import com.datadog.gradle.plugin.benchmark.model.BenchmarkResult
import com.google.gson.Gson
import java.io.File

class BenchmarkJsonFileVisitor {

    // region ReviewJsonFileVisitor

    fun visitBenchmarkJsonFile(
        jsonFile: File,
        benchmarksAccumulator: MutableMap<String, Long>
    ) {
        val gson = Gson()
        val benchmarkResult = gson.fromJson(jsonFile.readText(), BenchmarkResult::class.java)

        println(benchmarkResult.context.targetBuild)

        benchmarkResult.benchmarks.forEach {
            benchmarksAccumulator[it.nameWithoutPrefixes()] = it.metrics.timeNs.median
        }
    }

    // endregion

    // region Internal

    private fun Benchmark.nameWithoutPrefixes(): String {
        return devicePrefixes.fold(name) { acc, prefix ->
            acc.replace("${prefix}_", "")
        }
    }

    // endregion

    companion object {
        private val devicePrefixes = listOf(
            "EMULATOR",
            "UNLOCKED",
            "DEBUGGABLE",
            "ENG-BUILD"
        )
    }
}
