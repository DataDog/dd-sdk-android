package com.datadog.timeseries.encoding

import com.datadog.timeseries.models.TimeseriesEvent
import kotlinx.serialization.json.Json

class TimeseriesEncoder {

    private val json = Json {
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(event: TimeseriesEvent): String {
        return json.encodeToString(TimeseriesEvent.serializer(), event)
    }
}
