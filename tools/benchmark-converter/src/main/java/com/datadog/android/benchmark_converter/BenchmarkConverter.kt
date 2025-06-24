/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.benchmark_converter

import com.datadog.android.benchmark_converter.models.CBMFResult
import com.datadog.android.benchmark_converter.models.MacrobenchmarkResult
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("benchmark-converter")
    val resultPath by parser.option(ArgType.String, shortName = "r", description = "Path to the result file").required()
    parser.parse(args)

    val txt = File(resultPath).readText()
    val result = Json.Default.decodeFromString<MacrobenchmarkResult>(txt)

    val converted = CBMFResult(
        schema_version = "v1",
        benchmarks = result.benchmarks.flatMap { benchmark ->
            val metrics = benchmark.metrics.map { (metricName, metric) ->
                CBMFResult.Benchmark(
                    parameters = mapOf(
                        "scenario" to "${benchmark.name}:${metricName}",
                        "className" to "baseline",
                    ),
                    runs = metric.runs.mapIndexedNotNull { index, x ->
                        cbmfMetricName(metricName)?.let { cbmfMetricName ->
                            cbmfUom(metricName)?.let { cbmfUom ->
                                "run$index" to mapOf(cbmfMetricName to CBMFResult.Measurement(cbmfUom, listOf(x)))
                            }
                        }
                    }.toMap()
                )
            }

            val sampledMetric = result.benchmarks.flatMap { benchmark ->
                benchmark.sampledMetrics.map { (metricName, metric) ->
                    CBMFResult.Benchmark(
                        parameters = mapOf(
                            "scenario" to "${benchmark.name}:${metricName}",
                            "className" to "baseline",
                        ),
                        runs = metric.runs.mapIndexedNotNull { index, x ->
                            cbmfMetricName(metricName)?.let { cbmfMetricName ->
                                cbmfUom(metricName)?.let { cbmfUom ->
                                    "run$index" to mapOf(cbmfMetricName to CBMFResult.Measurement(cbmfUom, x))
                                }
                            }
                        }.toMap()
                    )
                }
            }

            metrics + sampledMetric
        }
    )

    println(
        Json {
            prettyPrint = true
        }.encodeToString(converted)
    )
}

private fun cbmfMetricName(metricName: String): String? {
    return when (metricName) {
        "memoryHeapSizeMaxKb", "memoryRssAnonMaxKb", "memoryRssFileMaxKb" -> "rss"
        "timeToInitialDisplayMs", "frameDurationCpuMs", "frameOverrunMs" -> "execution_time"
        "frameCount" -> "iterations"
        else -> null
    }
}

private fun cbmfUom(metricName: String): String? {
    return when (metricName) {
        "memoryHeapSizeMaxKb", "memoryRssAnonMaxKb", "memoryRssFileMaxKb" -> "KB"
        "timeToInitialDisplayMs", "frameDurationCpuMs", "frameOverrunMs" -> "ms"
        "frameCount" -> "iterations"
        else -> null
    }
}
