package com.datadog.android.utils.extension

import com.datadog.android.log.assertj.JsonObjectAssert
import com.datadog.android.tracing.internal.domain.SpanSerializer
import com.google.gson.JsonObject
import datadog.opentracing.DDSpan
import java.lang.Long

fun JsonObject.assertMatches(span: DDSpan) {
    JsonObjectAssert.assertThat(this)
        .hasField(SpanSerializer.START_TIMESTAMP_KEY, span.startTime)
        .hasField(SpanSerializer.DURATION_KEY, span.durationNano)
        .hasField(SpanSerializer.SERVICE_NAME_KEY, span.serviceName)
        .hasField(SpanSerializer.TRACE_ID_KEY, Long.toHexString((span.traceId.toLong())))
        .hasField(SpanSerializer.SPAN_ID_KEY, Long.toHexString((span.spanId.toLong())))
        .hasField(SpanSerializer.PARENT_ID_KEY, Long.toHexString((span.parentId.toLong())))
        .hasField(SpanSerializer.RESOURCE_KEY, span.resourceName)
        .hasField(SpanSerializer.OPERATION_NAME_KEY, span.operationName)
        .hasField(SpanSerializer.META_KEY, span.meta)
        .hasField(SpanSerializer.METRICS_KEY, span.metrics)
}
