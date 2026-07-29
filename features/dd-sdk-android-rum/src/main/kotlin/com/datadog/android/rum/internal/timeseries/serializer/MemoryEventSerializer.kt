/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.toTimeseriesMemorySessionType
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.google.gson.JsonElement
import java.util.UUID

@Suppress("LongParameterList")
internal class MemoryEventSerializer(
    private val sessionId: String,
    private val sessionType: RumSessionType,
    private val totalRamBytes: Long,
    private val timeProvider: TimeProvider,
    private val rumContext: RumContext
) : JsonSerializer<Double> {

    override fun serialize(datadogContext: DatadogContext, dataPoints: List<DataPoint<Double>>): JsonElement? {
        if (totalRamBytes <= 0L || dataPoints.isEmpty()) return null
        return TimeseriesMemoryEvent(
            dd = TimeseriesMemoryEvent.Dd(),
            application = TimeseriesMemoryEvent.Application(id = rumContext.applicationId),
            session = TimeseriesMemoryEvent.TimeseriesMemoryEventSession(
                id = sessionId,
                type = sessionType.toTimeseriesMemorySessionType()
            ),
            source = TimeseriesMemoryEvent.Source.ANDROID,
            date = timeProvider.getDeviceTimestampMillis(),
            version = datadogContext.version,
            service = datadogContext.service,
            view = TimeseriesMemoryEvent.View(
                referrer = null,
                name = rumContext.viewName,
                url = rumContext.viewUrl ?: "",
                id = rumContext.viewId ?: RumContext.NULL_UUID
            ),
            timeseries = TimeseriesMemoryEvent.Timeseries(
                id = UUID.randomUUID().toString(),
                start = dataPoints.firstOrNull()?.timestampNs ?: 0L,
                end = dataPoints.lastOrNull()?.timestampNs ?: 0L,
                data = TimeseriesMemoryEvent.Data(
                    timestamps = dataPoints.map { it.timestampNs },
                    values = TimeseriesMemoryEvent.Values(
                        memoryPercent = dataPoints.map { it.value / totalRamBytes * PERCENT_FACTOR },
                        memoryFootprint = dataPoints.map { it.value / BYTES_IN_KB }
                    )
                )
            )
        ).toJson()
    }

    private companion object {
        const val PERCENT_FACTOR = 100.0

        // Must match the factor used in MemoryVitalReader when converting VmRSS kB to bytes.
        // Datadog memory vitals use decimal (SI) units, so 1000 (not 1024) keeps the emitted
        // memory_footprint consistent with the collected value and with memory_percent.
        const val BYTES_IN_KB = 1000
    }
}
