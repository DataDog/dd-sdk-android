/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2026-Present Datadog, Inc.
 */

package com.datadog.flags.benchmark

import com.google.protobuf.util.JsonFormat
import datadog.ffe.flagging.ufc.v1.Ufc
import java.io.File
import java.util.Base64
import kotlin.math.ceil

/** Benchmark-only facade. The RN adapter serializes access, including installations. */
class FlagsBenchmark(private val cacheFile: File) {
    private var bytes: ByteArray? = null
    private var evaluator: RulesEvaluator? = null

    fun run(request: Map<String, Any?>): Map<String, Any?> = try {
        execute(request)
    } catch (exception: Exception) {
        mapOf("benchmarkError" to "${exception.javaClass.simpleName}: ${exception.message}")
    }

    private fun execute(request: Map<String, Any?>): Map<String, Any?> = when (request["op"]) {
        "preload" -> {
            val decoded = Base64.getDecoder().decode(request["base64"] as String)
            bytes = decoded
            mapOf("byteCount" to decoded.size)
        }
        "installBinary" -> install(decode())
        "installJson" -> {
            val builder = Ufc.FlagsConfiguration.newBuilder()
            JsonFormat.parser().merge(request["json"] as String, builder)
            install(builder.build())
        }
        "binaryToJson" -> mapOf("json" to JsonFormat.printer().omittingInsignificantWhitespace().print(decode()))
        "evaluate" -> requireNotNull(evaluator) { "Not initialized" }.evaluate(EvaluationRequest.from(request))
        "persistBinary" -> {
            val data = requireNotNull(bytes) { "No preloaded bytes" }
            // Each adapter owns a temporary cache file; no SDK or customer cache is touched.
            cacheFile.parentFile?.mkdirs()
            val temporary = File(cacheFile.path + ".tmp")
            temporary.writeBytes(data)
            check(temporary.renameTo(cacheFile)) { "Could not replace benchmark cache" }
            mapOf("byteCount" to data.size)
        }
        "readCachedBinary" -> mapOf("base64" to Base64.getEncoder().encodeToString(cacheFile.readBytes()))
        "readCachedJson" -> mapOf("json" to JsonFormat.printer().omittingInsignificantWhitespace().print(
            Ufc.FlagsConfiguration.parseFrom(cacheFile.readBytes())
        ))
        "evaluateCachedBinary" -> {
            install(Ufc.FlagsConfiguration.parseFrom(cacheFile.readBytes()))
            requireNotNull(evaluator).evaluate(EvaluationRequest.from(request))
        }
        "echo" -> {
            @Suppress("UNCHECKED_CAST")
            (request["result"] as Map<String, Any?>)
        }
        "controls" -> controls(request)
        else -> error("Unknown benchmark operation: ${request["op"]}")
    }

    private fun decode() = Ufc.FlagsConfiguration.parseFrom(requireNotNull(bytes) { "No preloaded bytes" })

    private fun install(configuration: Ufc.FlagsConfiguration): Map<String, Any?> {
        evaluator = RulesEvaluator(configuration)
        return mapOf("flagCount" to configuration.flagsCount)
    }

    private fun controls(request: Map<String, Any?>): Map<String, Any?> {
        val raw = request["requests"] as List<*>
        require(raw.size in 1..10_000)
        val requests = raw.map {
            @Suppress("UNCHECKED_CAST")
            EvaluationRequest.from(it as Map<String, Any?>)
        }
        val count = positiveCount(request["iterations"])
        val warmup = positiveCount(request["warmup"])
        val decodeCount = positiveCount(request["decodeIterations"])
        val engine = RulesEvaluator(decode())
        val decodeTimes = mutableListOf<Double>()
        val evaluationTimes = mutableListOf<Double>()
        val clockTimes = mutableListOf<Double>()
        var flagCount = 0
        var checksum = 0
        var warmupChecksum = 0
        repeat(decodeCount) {
            val start = System.nanoTime()
            val decoded = decode()
            decodeTimes.add(elapsedMs(start))
            flagCount = decoded.flagsCount
        }
        repeat(warmup) {
            val result = engine.evaluate(requests[it % requests.size])
            check(result["reason"] != "ERROR") { "Unsupported native workload" }
            if (result["value"] == true) warmupChecksum++
        }
        repeat(count) {
            val input = requests[it % requests.size]
            val start = System.nanoTime()
            val result = engine.evaluate(input)
            evaluationTimes.add(elapsedMs(start))
            check(result["reason"] != "ERROR") { "Unsupported native workload" }
            if (result["value"] == true) checksum++
            val clockStart = System.nanoTime()
            clockTimes.add(elapsedMs(clockStart))
        }
        return mapOf(
            "decode" to summarize(decodeTimes), "directEvaluation" to summarize(evaluationTimes),
            "clock" to summarize(clockTimes), "flagCount" to flagCount, "checksum" to checksum,
            "warmupChecksum" to warmupChecksum,
            "inputConversion" to "preconverted; excluded from native-direct control"
        )
    }

    private fun positiveCount(value: Any?): Int {
        val number = (value as? Number)?.toDouble() ?: error("Invalid count")
        require(number.isFinite() && number % 1.0 == 0.0 && number in 1.0..100_000.0)
        return number.toInt()
    }

    companion object {
        private fun elapsedMs(start: Long) = (System.nanoTime() - start) / 1_000_000.0

        internal fun summarize(values: List<Double>): Map<String, Any> {
            require(values.isNotEmpty())
            val sorted = values.sorted()
            fun percentile(fraction: Double) = sorted[ceil(fraction * sorted.size).toInt() - 1] * 1_000
            return mapOf(
                "count" to sorted.size, "p50Us" to percentile(0.5), "p95Us" to percentile(0.95),
                "p99Us" to percentile(0.99), "meanUs" to values.average() * 1_000, "maxUs" to sorted.last() * 1_000
            )
        }
    }
}
