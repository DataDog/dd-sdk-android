package com.datadog.android.tracing.internal.domain

import com.datadog.android.core.internal.domain.Serializer
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import datadog.opentracing.DDSpan

internal class SpanSerializer : Serializer<DDSpan> {
    private val mapper: ObjectMapper = ObjectMapper(JsonFactory())

    override fun serialize(model: DDSpan): String {
        return mapper.writeValueAsString(model)
    }

    companion object {
        const val START_TIMESTAMP_KEY = "start"
        const val DURATION_KEY = "duration"
        const val SERVICE_NAME_KEY = "service"
        const val TRACE_ID_KEY = "trace_id"
        const val SPAN_ID_KEY = "span_id"
        const val PARENT_ID_KEY = "parent_id"
        const val RESOURCE_KEY = "resource"
        const val OPERATION_NAME_KEY = "name"
    }
}
