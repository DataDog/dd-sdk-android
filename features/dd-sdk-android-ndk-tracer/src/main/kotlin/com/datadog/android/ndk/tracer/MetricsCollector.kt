/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ndk.tracer

import java.io.File
import java.util.LinkedList
import kotlin.system.measureNanoTime

internal class MetricsCollector(private val methodName: String, metricsCollectorDirectory: File? = null) {

    private var durationInMs: Double = 0.0
    private var count: Long = 0
    private val metricsHistory:LinkedList<Double> = LinkedList()
    private val metricsCollectorFile: File? = metricsCollectorDirectory?.let {
        File(it, "metrics_collector_$methodName.txt")
    }

    internal fun measureMethodDuration(block: () -> Unit) {

        val duration = measureNanoTime(block).toMilliseconds()
        addDuration(duration)
    }

    private fun Long.toMilliseconds(): Double {
        return this / 1_000_000.0
    }

    fun addDuration(duration: Double) {
        metricsHistory.add(duration)
        durationInMs += duration
        count++
    }

    fun dumpMetrics() {
        metricsCollectorFile?.let { file ->
            if (!file.exists()) {
                file.createNewFile()
            }
            val metricsAsString = metricsHistory.joinToString(separator = ",") { it.toString() }
            file.writeText(metricsAsString)
        }
        metricsHistory.clear()
    }

    fun printMean() {
        if (count == 0L) {
            println("No data collected for method: $methodName")
            return
        }
        val mean = durationInMs / count
        println("Method: $methodName, Mean duration: $mean ms")
    }
}