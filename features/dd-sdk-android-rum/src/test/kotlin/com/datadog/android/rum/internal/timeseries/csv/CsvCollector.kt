/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.csv

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.scope.RumViewType
import com.datadog.android.rum.internal.timeseries.Buffer
import com.datadog.android.rum.internal.timeseries.TimeseriesCollector
import com.datadog.android.rum.internal.timeseries.serializer.CpuEventSerializer
import com.datadog.android.rum.internal.timeseries.serializer.JsonSerializer
import com.datadog.android.rum.internal.timeseries.serializer.MemoryEventSerializer
import com.google.gson.JsonObject

/**
 * A test-only [Timeseries] that mirrors the wiring done by `DefaultTimeseriesCollectorFactory`,
 * but pulls samples from CSV-backed readers instead of `VitalReaderWrapper` and drives the
 * pipelines synchronously (no executor) so callers can assert on every emitted JSON.
 */
internal class CsvCollector(
    private val pipelines: List<Triple<CSVReader, Buffer<Double>, JsonSerializer<Double>>>,
    private val datadogContext: DatadogContext
) : TimeseriesCollector {

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
                if (buffer.isFull()) flush(buffer, serializer)
            }
            flush(buffer, serializer)
        }
    }

    private fun flush(buffer: Buffer<Double>, serializer: JsonSerializer<Double>) = buffer.drain()
        .takeIf { it.isNotEmpty() }
        ?.let { serializer.serialize(datadogContext, it) }
        ?.let(emitted::add)

    override fun onSessionStop() = Unit

    override fun onViewTypeUpdate(newViewType: RumViewType) = Unit

    companion object {

        /**
         * Builds a [CsvCollector] with the same shape as `DefaultTimeseriesCollectorFactory`:
         * a memory pipeline followed by a CPU pipeline, each backed by [CSVReader] and the
         * production [MemoryEventSerializer] / [CpuEventSerializer].
         */
        @Suppress("LongParameterList")
        fun create(
            csvContent: String,
            sessionId: String,
            applicationId: String,
            sessionType: RumSessionType,
            totalRamBytes: Long,
            bufferSize: Int,
            timeProvider: TimeProvider,
            datadogContext: DatadogContext
        ) = CsvCollector(
            datadogContext = datadogContext,
            pipelines = listOf(
                Triple(
                    CSVReader(csvContent, METRIC_MEMORY_USAGE, timeProvider),
                    Buffer(bufferSize),
                    MemoryEventSerializer(
                        sessionId = sessionId,
                        applicationId = applicationId,
                        sessionType = sessionType,
                        totalRamBytes = totalRamBytes,
                        timeProvider = timeProvider
                    )
                ),
                Triple(
                    CSVReader(csvContent, METRIC_CPU_USAGE, timeProvider),
                    Buffer(bufferSize),
                    CpuEventSerializer(
                        sessionId = sessionId,
                        applicationId = applicationId,
                        sessionType = sessionType,
                        timeProvider = timeProvider
                    )
                )
            )
        )

        const val METRIC_MEMORY_USAGE: String = "memory_usage"
        const val METRIC_CPU_USAGE: String = "cpu_usage"
    }
}
