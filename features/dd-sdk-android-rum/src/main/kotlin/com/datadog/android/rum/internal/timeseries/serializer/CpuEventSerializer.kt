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
import com.datadog.android.rum.internal.toTimeseriesCpuSessionType
import com.datadog.android.rum.model.TimeseriesCpuEvent
import com.google.gson.JsonObject
import java.util.UUID
import kotlin.math.pow

@Suppress("LongParameterList")
internal class CpuEventSerializer(
    private val sessionId: String,
    private val applicationId: String,
    private val sessionType: RumSessionType,
    private val timeProvider: TimeProvider,
    private val additionalAttributes: Map<String, String>,
    private val useDeltaCompression: Boolean = false,
    private val precision: Int = DeltaCompression.PRECISION
) : JsonSerializer<Double> {

    override fun serialize(datadogContext: DatadogContext, dataPoints: List<DataPoint<Double>>): JsonObject? {
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
            version = datadogContext.version,
            service = datadogContext.service,
            timeseries = TimeseriesCpuEvent.Timeseries(
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
        if (additionalAttributes.isNotEmpty()) {
            timeseriesJson?.add(
                TimeseriesAttributes.KEY_TAGS,
                additionalAttributes.toJson()
            )
        }
        timeseriesJson?.addProperty(TimeseriesAttributes.KEY_COUNT, data.size)
        // </DOGFOODING ONLY>
        return json
    }

    private fun encodeDelta(data: List<TimeseriesCpuEvent.Data>): JsonObject? {
        if (data.size <= 1) return null

        val scale = 10.0.pow(precision.toDouble()).toLong()
        val ts = data.mapToDeltaCompressed { it.timestamp }
        val cpuUsageArray = data.mapToDeltaCompressed {
            roundToLongSafely(it.dataPoint.cpuUsage.toDouble(), scale = scale)
        }

        return JsonObject().apply {
            addProperty(TimeseriesAttributes.KEY_PRECISION, precision)
            addProperty(TimeseriesAttributes.KEY_RESOLUTION, TimeseriesAttributes.NS)
            add(TimeseriesAttributes.KEY_TS, ts)
            add(TimeseriesAttributes.KEY_VALUE, cpuUsageArray)
        }
    }
}
