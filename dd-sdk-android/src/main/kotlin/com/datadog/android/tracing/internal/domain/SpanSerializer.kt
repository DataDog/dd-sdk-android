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
}
