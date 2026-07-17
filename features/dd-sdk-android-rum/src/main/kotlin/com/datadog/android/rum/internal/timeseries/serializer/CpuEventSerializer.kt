/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.toTimeseriesCpuSessionType
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.google.gson.JsonObject
import java.util.UUID

@Suppress("LongParameterList")
internal class CpuEventSerializer(
    private val sessionId: String,
    private val applicationId: String,
    private val sessionType: RumSessionType,
    private val timeProvider: TimeProvider
) : JsonSerializer<Double> {

    override fun serialize(datadogContext: DatadogContext, dataPoints: List<DataPoint<Double>>): JsonObject? {
        if (dataPoints.isEmpty()) return null
        return TimeseriesCpuEvent(
            dd = TimeseriesCpuEvent.Dd(),
            application = TimeseriesCpuEvent.Application(id = applicationId),
            session = TimeseriesCpuEvent.Session(id = sessionId, type = sessionType.toTimeseriesCpuSessionType()),
            source = TimeseriesCpuEvent.Source.ANDROID,
            date = timeProvider.getDeviceTimestampMillis(),
            version = datadogContext.version,
            service = datadogContext.service,
            timeseries = TimeseriesCpuEvent.Timeseries(
                id = UUID.randomUUID().toString(),
                start = dataPoints.firstOrNull()?.timestampNs ?: 0L,
                end = dataPoints.lastOrNull()?.timestampNs ?: 0L,
                data = TimeseriesCpuEvent.Data(
                    timestamps = dataPoints.map { it.timestampNs },
                    values = TimeseriesCpuEvent.Values(dataPoints.map { it.value })
                )
            )
        ).toJson().asJsonObject
    }
}
