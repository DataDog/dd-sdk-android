/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.csv

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.InfoData
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.domain.event.RumEventSerializer
import com.datadog.android.rum.internal.timeseries.Buffer
import com.datadog.android.rum.internal.timeseries.MemorySnapshot
import com.datadog.android.rum.internal.timeseries.TimeseriesCollector
import com.datadog.android.rum.internal.timeseries.factory.CpuEventFactory
import com.datadog.android.rum.internal.timeseries.factory.EventFactory
import com.datadog.android.rum.internal.timeseries.factory.MemoryEventFactory
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge

/**
 * A test-only [TimeseriesCollector] that mirrors the wiring done by `DefaultTimeseriesCollector`,
 * but pulls samples from CSV-backed readers instead of the `/proc` readers and drives the
 * pipelines synchronously (no executor) so callers can assert on every emitted JSON.
 */
internal class CsvCollector(
    private val pipelines: List<CsvPipeline<*>>,
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
    override fun onSessionStart(sessionId: String, sessionType: RumSessionType) {
        pipelines.forEach { it.run() }
    }

    override fun isTimeseriesCollected(sessionId: String): Boolean = emitted.isNotEmpty()

    private fun <T : Any> CsvPipeline<T>.run() {
        while (reader.hasNext()) {
            buffer.add(reader.read())
            if (buffer.isFull()) flush(buffer, eventFactory)
        }
        flush(buffer, eventFactory)
    }

    private fun <T : Any> flush(buffer: Buffer<T>, eventFactory: EventFactory<T, *>) = buffer.drain()
        .takeIf { it.isNotEmpty() }
        ?.let { eventFactory.create(datadogContext, rumContext, it) }
        ?.let(eventSerializer::serialize)
        ?.let(JsonParser::parseString)
        ?.let { emitted.add(it.asJsonObject) }

    override fun onSessionStop(sessionId: String) = Unit

    override fun onRumContextUpdate(newRumContext: RumContext) = Unit

    override fun onUiVisible() = Unit

    override fun onUiHidden() = Unit

    companion object {

        /**
         * Builds a [CsvCollector] with the same shape as `DefaultTimeseriesCollector`:
         * a memory pipeline followed by a CPU pipeline, each backed by [CSVReader] and the
         * production [MemoryEventFactory] / [CpuEventFactory].
         */
        fun create(
            csvContent: String,
            sessionType: RumSessionType,
            bufferSize: Int,
            timeProvider: TimeProvider,
            serverTimeOffsetNs: Long,
            forge: Forge,
            internalLogger: InternalLogger
        ): CsvCollector {
            val rumContext = forge.getForgery<RumContext>()
            val datadogContext = forge.getForgery<DatadogContext>().let {
                it.copy(time = it.time.copy(serverTimeOffsetNs = serverTimeOffsetNs))
            }
            val batteryInfo = forge.getForgery<BatteryInfo>()
            val displayInfo = forge.getForgery<DisplayInfo>()

            return CsvCollector(
                datadogContext = datadogContext,
                rumContext = rumContext,
                eventSerializer = RumEventSerializer(internalLogger),
                pipelines = listOf(
                    CsvPipeline(
                        CSVReader(csvContent, METRIC_MEMORY, timeProvider) { parseMemorySnapshot(it) },
                        Buffer(bufferSize),
                        MemoryEventFactory(
                            sessionType = sessionType,
                            batteryInfoProvider = FixedInfoProvider(batteryInfo),
                            displayInfoProvider = FixedInfoProvider(displayInfo),
                            internalLogger = internalLogger
                        )
                    ),
                    CsvPipeline(
                        CSVReader(csvContent, METRIC_CPU_USAGE, timeProvider) { parseCpuUsage(it) },
                        Buffer(bufferSize),
                        CpuEventFactory(
                            sessionType = sessionType,
                            batteryInfoProvider = FixedInfoProvider(batteryInfo),
                            displayInfoProvider = FixedInfoProvider(displayInfo),
                            internalLogger = internalLogger
                        )
                    )
                )
            )
        }

        private fun parseMemorySnapshot(values: List<String>): MemorySnapshot? {
            val valuesKib = values.mapNotNull { it.toLongOrNull() }
            return if (values.size == MEMORY_VALUES_COUNT && valuesKib.size == MEMORY_VALUES_COUNT) {
                MemorySnapshot(footprintKib = valuesKib[0], nonResidentKib = valuesKib[1])
            } else {
                null
            }
        }

        private fun parseCpuUsage(values: List<String>): Double? = values.singleOrNull()?.toDoubleOrNull()

        private class FixedInfoProvider<T : InfoData>(private val state: T) : InfoProvider<T> {
            override fun getState(): T = state
            override fun cleanup() = Unit
        }

        private const val MEMORY_VALUES_COUNT = 2

        const val METRIC_MEMORY: String = "memory"
        const val METRIC_CPU_USAGE: String = "cpu_usage"
    }
}

internal class CsvPipeline<T : Any>(
    val reader: CSVReader<T>,
    val buffer: Buffer<T>,
    val eventFactory: EventFactory<T, *>
)
