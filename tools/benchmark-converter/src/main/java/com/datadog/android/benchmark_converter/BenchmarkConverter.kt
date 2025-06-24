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
            benchmark.metrics.map { (metricName, metric) ->
                CBMFResult.Benchmark(
                    parameters = mapOf(
                        "scenario" to "${benchmark.name}:${metricName}",
                        "className" to benchmark.className,
                    ),
                    runs = metric.runs.mapIndexed { index, x ->
                        "run$index" to mapOf("execution_time" to CBMFResult.Measurement("ms", listOf(x)))
                    }.toMap()
                )
            }
        }
    )

    println(
        Json {
            prettyPrint = true
        }.encodeToString(converted)
    )
}
