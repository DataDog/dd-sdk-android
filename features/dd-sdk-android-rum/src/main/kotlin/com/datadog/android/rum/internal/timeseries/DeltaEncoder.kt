/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries

import com.datadog.android.rum.model.RumTimeseriesCpuEvent
import com.datadog.android.rum.model.RumTimeseriesMemoryEvent
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.math.roundToLong

internal object DeltaEncoder {

    private const val PRECISION = 4
    private val SCALE: Long = 10_000L // 10^4

    /**
     * Returns null if buffer has ≤1 sample.
     * Output JsonObject:
     * { "precision": 4, "ts": [absoluteNs, delta1, delta2, ...], "memory_max": [...], "memory_percent": [...] }
     * All values are Long (JSON integers).
     */
    fun encodeMemory(buffer: List<RumTimeseriesMemoryEvent.Data>): JsonObject? {
        if (buffer.size <= 1) return null

        val tsArray = JsonArray()
        val memoryMaxArray = JsonArray()
        val memoryPercentArray = JsonArray()

        val scaledMemoryMax = buffer.map { (it.dataPoint.memoryMax.toDouble() * SCALE).roundToLong() }
        val scaledMemoryPercent = buffer.map { (it.dataPoint.memoryPercent.toDouble() * SCALE).roundToLong() }

        for (i in buffer.indices) {
            if (i == 0) {
                tsArray.add(buffer[0].timestamp)
                memoryMaxArray.add(scaledMemoryMax[0])
                memoryPercentArray.add(scaledMemoryPercent[0])
            } else {
                tsArray.add(buffer[i].timestamp - buffer[i - 1].timestamp)
                memoryMaxArray.add(scaledMemoryMax[i] - scaledMemoryMax[i - 1])
                memoryPercentArray.add(scaledMemoryPercent[i] - scaledMemoryPercent[i - 1])
            }
        }

        return JsonObject().apply {
            addProperty("precision", PRECISION)
            add("ts", tsArray)
            add("memory_max", memoryMaxArray)
            add("memory_percent", memoryPercentArray)
        }
    }

    /**
     * Returns null if buffer has ≤1 sample.
     * Output: { "precision": 4, "ts": [...], "cpu_usage": [...] }
     */
    fun encodeCpu(buffer: List<RumTimeseriesCpuEvent.Data>): JsonObject? {
        if (buffer.size <= 1) return null

        val tsArray = JsonArray()
        val cpuUsageArray = JsonArray()

        val scaledCpuUsage = buffer.map { (it.dataPoint.cpuUsage.toDouble() * SCALE).roundToLong() }

        for (i in buffer.indices) {
            if (i == 0) {
                tsArray.add(buffer[0].timestamp)
                cpuUsageArray.add(scaledCpuUsage[0])
            } else {
                tsArray.add(buffer[i].timestamp - buffer[i - 1].timestamp)
                cpuUsageArray.add(scaledCpuUsage[i] - scaledCpuUsage[i - 1])
            }
        }

        return JsonObject().apply {
            addProperty("precision", PRECISION)
            add("ts", tsArray)
            add("cpu_usage", cpuUsageArray)
        }
    }
}
