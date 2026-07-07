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
import com.datadog.android.rum.internal.timeseries.DeltaCompression
import com.datadog.android.rum.internal.timeseries.DeltaCompression.mapToDeltaCompressed
import com.datadog.android.rum.internal.timeseries.DeltaCompression.roundToLongSafely
import com.datadog.android.rum.internal.toTimeseriesMemorySessionType
import com.datadog.android.rum.model.TimeseriesMemoryEvent
import com.google.gson.JsonObject
import java.util.UUID
import kotlin.math.pow

@Suppress("LongParameterList")
internal class MemoryEventSerializer(
    private val sessionId: String,
    private val applicationId: String,
    private val sessionType: RumSessionType,
    private val totalRamBytes: Long,
    private val timeProvider: TimeProvider,
    private val additionalAttributes: Map<String, String>,
    private val useDeltaCompression: Boolean = false,
    private val precision: Int = DeltaCompression.PRECISION
) : JsonSerializer<Double> {

    override fun serialize(datadogContext: DatadogContext, dataPoints: List<DataPoint<Double>>): JsonObject? {
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
            version = datadogContext.version,
            service = datadogContext.service,
            timeseries = TimeseriesMemoryEvent.Timeseries(
                id = UUID.randomUUID().toString(),
                schema = schema,
                start = start,
                end = end,
                data = data
            )
        ).toJson().asJsonObject
        // <DOGFOODING ONLY>
        val timeseriesJson = json.getAsJsonObject(TimeseriesAttributes.KEY_TIMESERIES)
        if (deltaEncoded != null) {
            timeseriesJson?.remove(TimeseriesAttributes.KEY_DATA)
            timeseriesJson?.add(TimeseriesAttributes.KEY_DATA, deltaEncoded)
        }
        timeseriesJson?.addProperty(TimeseriesAttributes.KEY_COUNT, data.size)
        if (additionalAttributes.isNotEmpty()) {
            timeseriesJson?.add(
                TimeseriesAttributes.KEY_TAGS,
                additionalAttributes.toJson()
            )
        }
        // </DOGFOODING ONLY>
        return json
    }

    private fun encodeDelta(data: List<TimeseriesMemoryEvent.Data>): JsonObject? {
        if (data.size <= 1) return null

        val scale = 10.0.pow(precision.toDouble()).toLong()
        val ts = data.mapToDeltaCompressed { it.timestamp }

        val memoryFootprintArray = data.mapToDeltaCompressed {
            roundToLongSafely(it.dataPoint.memoryFootprint.toDouble(), scale = scale)
        }

        val memoryPercentArray = data.mapToDeltaCompressed {
            roundToLongSafely(it.dataPoint.memoryPercent.toDouble(), scale = scale)
        }

        return JsonObject().apply {
            addProperty(TimeseriesAttributes.KEY_PRECISION, precision)
            addProperty(TimeseriesAttributes.KEY_RESOLUTION, TimeseriesAttributes.NS)
            add(TimeseriesAttributes.KEY_TS, ts)
            add(TimeseriesAttributes.KEY_MEMORY_FOOTPRINT, memoryFootprintArray)
            add(TimeseriesAttributes.KEY_MEMORY_PERCENT, memoryPercentArray)
        }
    }

    companion object {
        private const val PERCENT_FACTOR = 100.0
    }
}
