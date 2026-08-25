/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.csv

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoData
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.timeseries.Buffer
import com.datadog.android.rum.internal.timeseries.TimeseriesCollector
import com.datadog.android.rum.internal.timeseries.factory.CpuEventFactory
import com.datadog.android.rum.internal.timeseries.factory.EventFactory
import com.datadog.android.rum.internal.timeseries.factory.MemoryEventFactory
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge

/**
 * A test-only [TimeseriesCollector] that mirrors the wiring done by `DefaultTimeseriesCollectorFactory`,
 * but pulls samples from CSV-backed readers instead of `VitalReaderWrapper` and drives the
 * pipelines synchronously (no executor) so callers can assert on every emitted JSON.
 */
internal class CsvCollector(
    private val pipelines: List<Triple<CSVReader, Buffer<Double>, EventFactory<Double, *>>>,
    private val datadogContext: DatadogContext,
    private val rumContext: RumContext,
    private val eventSerializer: RumEventSerializer
) : TimeseriesCollector {

    private val emitted = mutableListOf<JsonObject>()

    /** All JSON events produced by the pipelines so far. */
    val captured: List<JsonObject> get() = emitted.toList()

    /**
     * Walks each (reader, buffer, factory) triple synchronously: samples until the reader is
     * exhausted, flushing whenever the buffer is full, then emitting any final remainder.
     */
    override fun onSessionStart() {
        for ((reader, buffer, eventFactory) in pipelines) {
            while (reader.hasNext()) {
                buffer.add(reader.read())
                if (buffer.isFull()) flush(buffer, eventFactory)
            }
            flush(buffer, eventFactory)
        }
    }

    private fun flush(buffer: Buffer<Double>, eventFactory: EventFactory<Double, *>) = buffer.drain()
        .takeIf { it.isNotEmpty() }
        ?.let { eventFactory.create(datadogContext, rumContext, it) }
        ?.let(eventSerializer::serialize)
        ?.let(JsonParser::parseString)
        ?.let { emitted.add(it.asJsonObject) }

    override fun onSessionStop() = Unit

    override fun onRumContextUpdate(newRumContext: RumContext) = Unit

    companion object {

        /**
         * Builds a [CsvCollector] with the same shape as `DefaultTimeseriesCollectorFactory`:
         * a memory pipeline followed by a CPU pipeline, each backed by [CSVReader] and the
         * production [MemoryEventFactory] / [CpuEventFactory].
         */
        fun create(
            csvContent: String,
            sessionType: RumSessionType,
            totalRamBytes: Long,
            bufferSize: Int,
            timeProvider: TimeProvider,
            forge: Forge,
            internalLogger: InternalLogger
        ): CsvCollector {
            val rumContext = forge.getForgery<RumContext>()
            val datadogContext = forge.getForgery<DatadogContext>()
            val batteryInfo = forge.getForgery<BatteryInfo>()
            val displayInfo = forge.getForgery<DisplayInfo>()

            return CsvCollector(
                datadogContext = datadogContext,
                rumContext = rumContext,
                eventSerializer = RumEventSerializer(internalLogger),
                pipelines = listOf(
                    Triple(
                        CSVReader(csvContent, METRIC_MEMORY_USAGE, timeProvider),
                        Buffer(bufferSize),
                        MemoryEventFactory(
                            sessionType = sessionType,
                            totalRamBytes = totalRamBytes,
                            timeProvider = timeProvider,
                            batteryInfoProvider = FixedInfoProvider(batteryInfo),
                            displayInfoProvider = FixedInfoProvider(displayInfo),
                            internalLogger = internalLogger
                        )
                    ),
                    Triple(
                        CSVReader(csvContent, METRIC_CPU_USAGE, timeProvider),
                        Buffer(bufferSize),
                        CpuEventFactory(
                            sessionType = sessionType,
                            timeProvider = timeProvider,
                            batteryInfoProvider = FixedInfoProvider(batteryInfo),
                            displayInfoProvider = FixedInfoProvider(displayInfo),
                            internalLogger = internalLogger
                        )
                    )
                )
            )
        }

        private class FixedInfoProvider<T : InfoData>(private val state: T) : InfoProvider<T> {
            override fun getState(): T = state
            override fun cleanup() = Unit
        }

        const val METRIC_MEMORY_USAGE: String = "memory_usage"
        const val METRIC_CPU_USAGE: String = "cpu_usage"
    }
}
