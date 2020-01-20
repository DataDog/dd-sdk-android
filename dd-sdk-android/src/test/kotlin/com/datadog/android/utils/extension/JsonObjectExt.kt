package com.datadog.android.utils.extension

import com.datadog.android.log.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import datadog.opentracing.DDSpan

fun JsonObject.assertMatches(span: DDSpan) {
    JsonObjectAssert.assertThat(this)
        .hasField(SpanKeys.START_TIMESTAMP_KEY, span.startTime)
        .hasField(SpanKeys.DURATION_KEY, span.durationNano)
        .hasField(SpanKeys.SERVICE_NAME_KEY, span.serviceName)
        .hasField(SpanKeys.TRACE_ID_KEY, span.traceId)
        .hasField(SpanKeys.SPAN_ID_KEY, span.spanId)
        .hasField(SpanKeys.PARENT_ID_KEY, span.parentId)
        .hasField(SpanKeys.RESOURCE_KEY, span.resourceName)
        .hasField(SpanKeys.OPERATION_NAME_KEY, span.operationName)
}

object SpanKeys {
    const val START_TIMESTAMP_KEY = "start"
    const val DURATION_KEY = "duration"
    const val SERVICE_NAME_KEY = "service"
    const val TRACE_ID_KEY = "trace_id"
    const val SPAN_ID_KEY = "span_id"
    const val PARENT_ID_KEY = "parent_id"
    const val RESOURCE_KEY = "resource"
    const val OPERATION_NAME_KEY = "name"
}
