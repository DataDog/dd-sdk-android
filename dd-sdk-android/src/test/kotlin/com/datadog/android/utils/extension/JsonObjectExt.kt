package com.datadog.android.utils.extension

import com.datadog.android.log.assertj.JsonObjectAssert
import com.datadog.android.tracing.internal.domain.SpanSerializer
import com.google.gson.JsonObject
import datadog.opentracing.DDSpan

fun JsonObject.assertMatches(span: DDSpan) {
    JsonObjectAssert.assertThat(this)
        .hasField(SpanSerializer.START_TIMESTAMP_KEY, span.startTime)
        .hasField(SpanSerializer.DURATION_KEY, span.durationNano)
        .hasField(SpanSerializer.SERVICE_NAME_KEY, span.serviceName)
        .hasField(SpanSerializer.TRACE_ID_KEY, span.traceId)
        .hasField(SpanSerializer.SPAN_ID_KEY, span.spanId)
        .hasField(SpanSerializer.PARENT_ID_KEY, span.parentId)
        .hasField(SpanSerializer.RESOURCE_KEY, span.resourceName)
        .hasField(SpanSerializer.OPERATION_NAME_KEY, span.operationName)
}
