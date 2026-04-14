package com.datadog.timeseries.core

import com.datadog.timeseries.models.Sample
import com.datadog.timeseries.models.TimeseriesEvent
import com.datadog.timeseries.models.TimeseriesName

class TimeseriesEventBuilder(private val config: TimeseriesConfig) {

    fun build(
        samples: List<Sample>,
        name: TimeseriesName,
        eventId: String
    ): TimeseriesEvent {
        val start = samples.firstOrNull()?.timestamp ?: 0L
        val end = samples.lastOrNull()?.timestamp ?: 0L
        val dateMs = start / 1_000_000

        val dataPoints = samples.map { sample ->
            TimeseriesEvent.DataPoint(
                timestamp = sample.timestamp,
                dataPointValue = sample.value
            )
        }

        return TimeseriesEvent(
            dd = TimeseriesEvent.DD(formatVersion = 2),
            application = TimeseriesEvent.Application(id = config.applicationId),
            date = dateMs,
            session = TimeseriesEvent.Session(id = config.sessionId, type = config.sessionType),
            source = config.source,
            type = "timeseries",
            service = config.service,
            version = config.version,
            timeseries = TimeseriesEvent.Timeseries(
                id = eventId,
                name = name,
                start = start,
                end = end,
                data = dataPoints
            )
        )
    }
}
