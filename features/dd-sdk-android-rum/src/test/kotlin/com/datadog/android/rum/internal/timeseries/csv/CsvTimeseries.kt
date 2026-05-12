/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.csv

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.timeseries.Buffer
import com.datadog.android.rum.internal.timeseries.Timeseries
import com.datadog.android.rum.internal.timeseries.serializer.CpuEventSerializer
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.datadog.android.rum.internal.timeseries.serializer.MemoryEventSerializer
import com.google.gson.JsonObject

/**
 * A test-only [Timeseries] that mirrors the wiring done by `RumSessionScopeTimeseriesFactory`,
 * but pulls samples from CSV-backed readers instead of `VitalReaderWrapper` and drives the
 * pipelines synchronously (no executor) so callers can assert on every emitted JSON.
 */
internal class CsvTimeseries(private val pipelines: List<Triple<CSVReader, Buffer<Double>, JsonSerializer<Double>>>) :
    Timeseries {

    private val emitted = mutableListOf<JsonObject>()

    /** All JSON events produced by the pipelines so far. */
    val captured: List<JsonObject> get() = emitted.toList()

    /**
     * Walks each (reader, buffer, serializer) triple synchronously: samples until the reader is
     * exhausted, flushing whenever the buffer is full, then emitting any final remainder.
     */
    override fun onSessionStart() {
        for ((reader, buffer, serializer) in pipelines) {
            while (reader.hasNext()) {
                buffer.add(reader.read())
                if (buffer.isFull()) {
                    buffer.drain().takeIf { it.isNotEmpty() }?.let(serializer::serialize)?.let(emitted::add)
                }
            }
            buffer.drain().takeIf { it.isNotEmpty() }?.let(serializer::serialize)?.let(emitted::add)
        }
    }

    override fun onSessionStop() = Unit

    override fun onViewTypeUpdate(viewType: RumViewType) = Unit

    companion object {

        /**
         * Builds a [CsvTimeseries] with the same shape as `RumSessionScopeTimeseriesFactory`:
         * a memory pipeline followed by a CPU pipeline, each backed by [CSVReader] and the
         * production [MemoryEventSerializer] / [CpuEventSerializer].
         */
        fun create(
            csvContent: String,
            sessionId: String,
            applicationId: String,
            sessionType: RumSessionType,
            totalRamBytes: Long,
            bufferSize: Int,
            timeProvider: TimeProvider,
            useDeltaCompression: Boolean = false
        ): CsvTimeseries {
            val memoryReader = CSVReader(csvContent, METRIC_MEMORY_USAGE, timeProvider)
            val cpuReader = CSVReader(csvContent, METRIC_CPU_USAGE, timeProvider)

            return CsvTimeseries(
                pipelines = listOf(
                    Triple(
                        memoryReader,
                        Buffer(bufferSize),
                        MemoryEventSerializer(
                            sessionId = sessionId,
                            applicationId = applicationId,
                            sessionType = sessionType,
                            totalRamBytes = totalRamBytes,
                            timeProvider = timeProvider,
                            useDeltaCompression = useDeltaCompression
                        )
                    ),
                    Triple(
                        cpuReader,
                        Buffer(bufferSize),
                        CpuEventSerializer(
                            sessionId = sessionId,
                            applicationId = applicationId,
                            sessionType = sessionType,
                            timeProvider = timeProvider,
                            useDeltaCompression = useDeltaCompression
                        )
                    )
                )
            )
        }

        const val METRIC_MEMORY_USAGE: String = "memory_usage"
        const val METRIC_CPU_USAGE: String = "cpu_usage"
    }
}
