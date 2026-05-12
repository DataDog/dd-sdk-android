/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.serializer

import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.timeseries.DataPoint
import com.datadog.android.rum.internal.timeseries.DeltaCompression
import com.datadog.android.rum.internal.timeseries.DeltaCompression.mapToDeltaCompressed
import com.datadog.android.rum.internal.timeseries.DeltaCompression.roundToLongSafely
import com.datadog.android.rum.internal.toTimeseriesCpuSessionType
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.google.gson.JsonObject
import java.util.UUID

internal class CpuEventSerializer(
    private val sessionId: String,
    private val applicationId: String,
    private val sessionType: RumSessionType,
    private val timeProvider: TimeProvider,
    private val useDeltaCompression: Boolean = false
) : JsonSerializer<Double> {

    override fun serialize(dataPoints: List<DataPoint<Double>>): JsonObject? {
        if (dataPoints.isEmpty()) return null
        val data = dataPoints.map { sample ->
            TimeseriesCpuEvent.Data(
                timestamp = sample.timestampNs,
                dataPoint = TimeseriesCpuEvent.DataPoint(sample.value)
            )
        }
        val start = data.firstOrNull()?.timestamp ?: 0L
        val end = data.lastOrNull()?.timestamp ?: 0L
        val deltaEncoded = if (useDeltaCompression) encodeDelta(data) else null
        val schema = if (deltaEncoded != null) {
            TimeseriesCpuEvent.Schema.DELTA_SCALAR
        } else {
            TimeseriesCpuEvent.Schema.OBJECT
        }
        val json = TimeseriesCpuEvent(
            dd = TimeseriesCpuEvent.Dd(),
            application = TimeseriesCpuEvent.Application(id = applicationId),
            session = TimeseriesCpuEvent.Session(
                id = sessionId,
                type = sessionType.toTimeseriesCpuSessionType()
            ),
            source = TimeseriesCpuEvent.Source.ANDROID,
            date = timeProvider.getDeviceTimestampMillis(),
            service = null,
            version = null,
            timeseries = TimeseriesCpuEvent.Timeseries(
                id = UUID.randomUUID().toString(),
                schema = schema,
                start = start,
                end = end,
                data = data
            )
        ).toJson() as JsonObject

        if (deltaEncoded != null) {
            val timeseriesJson = json.getAsJsonObject("timeseries")
            timeseriesJson.remove("data")
            timeseriesJson.add("data", deltaEncoded)
        }
        return json
    }

    private fun encodeDelta(data: List<TimeseriesCpuEvent.Data>): JsonObject? {
        if (data.size <= 1) return null

        val ts = data.mapToDeltaCompressed { it.timestamp }
        val cpuUsageArray = data.mapToDeltaCompressed {
            roundToLongSafely(it.dataPoint.cpuUsage.toDouble(), replaceNaNWith = 0L)
        }

        return JsonObject().apply {
            addProperty("precision", DeltaCompression.PRECISION)
            addProperty("resolution", RESOLUTION_NS)
            add("ts", ts)
            add("value", cpuUsageArray)
        }
    }

    companion object {
        private const val RESOLUTION_NS = "ns"
    }
}
