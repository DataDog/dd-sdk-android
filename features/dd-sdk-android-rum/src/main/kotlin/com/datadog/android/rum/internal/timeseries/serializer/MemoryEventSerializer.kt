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
import com.datadog.android.rum.internal.toTimeseriesMemorySessionType
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.google.gson.JsonObject
import java.util.UUID
import kotlin.math.pow

internal class MemoryEventSerializer(
    private val sessionId: String,
    private val applicationId: String,
    private val sessionType: RumSessionType,
    private val totalRamBytes: Long,
    private val timeProvider: TimeProvider,
    private val useDeltaCompression: Boolean = false,
    private val precision: Int = DeltaCompression.PRECISION
) : JsonSerializer<Double> {

    override fun serialize(dataPoints: List<DataPoint<Double>>): JsonObject? {
        if (totalRamBytes <= 0L || dataPoints.isEmpty()) return null
        val data = dataPoints.map { sample ->
            val memoryPercent = sample.value / totalRamBytes * PERCENT_FACTOR
            TimeseriesMemoryEvent.Data(
                timestamp = sample.timestampNs,
                dataPoint = TimeseriesMemoryEvent.DataPoint(sample.value, memoryPercent)
            )
        }
        val start = data.firstOrNull()?.timestamp ?: 0L
        val end = data.lastOrNull()?.timestamp ?: 0L
        val deltaEncoded = if (useDeltaCompression) encodeDelta(data) else null
        val schema = if (deltaEncoded != null) {
            TimeseriesMemoryEvent.Schema.DELTA_OBJECT
        } else {
            TimeseriesMemoryEvent.Schema.OBJECT
        }
        val json = TimeseriesMemoryEvent(
            dd = TimeseriesMemoryEvent.Dd(),
            application = TimeseriesMemoryEvent.Application(id = applicationId),
            session = TimeseriesMemoryEvent.Session(
                id = sessionId,
                type = sessionType.toTimeseriesMemorySessionType()
            ),
            source = TimeseriesMemoryEvent.Source.ANDROID,
            date = timeProvider.getDeviceTimestampMillis(),
            service = null,
            version = null,
            timeseries = TimeseriesMemoryEvent.Timeseries(
                id = UUID.randomUUID().toString(),
                schema = schema,
                start = start,
                end = end,
                data = data
            )
        ).toJson() as? JsonObject
        // <DOGFOODING ONLY>
        val timeseriesJson = json?.getAsJsonObject("timeseries")
        if (deltaEncoded != null) {
            timeseriesJson?.remove("data")
            timeseriesJson?.add("data", deltaEncoded)
        }
        timeseriesJson?.addProperty("count", data.size)
        // </DOGFOODING ONLY>
        return json
    }

    private fun encodeDelta(data: List<TimeseriesMemoryEvent.Data>): JsonObject? {
        if (data.size <= 1) return null

        val scale = 10.0.pow(precision.toDouble()).toLong()
        val ts = data.mapToDeltaCompressed { it.timestamp }

        val memoryMaxArray = data.mapToDeltaCompressed {
            roundToLongSafely(it.dataPoint.memoryMax.toDouble(), scale = scale)
        }

        val memoryPercentArray = data.mapToDeltaCompressed {
            roundToLongSafely(it.dataPoint.memoryPercent.toDouble(), scale = scale)
        }

        return JsonObject().apply {
            addProperty("precision", precision)
            addProperty("resolution", RESOLUTION_NS)
            add("ts", ts)
            add("memory_max", memoryMaxArray)
            add("memory_percent", memoryPercentArray)
        }
    }

    companion object {
        private const val PERCENT_FACTOR = 100.0
        private const val RESOLUTION_NS = "ns"
    }
}
