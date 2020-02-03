package com.datadog.android.tracing.internal.utils

import datadog.opentracing.DDSpan
import io.opentracing.Span
import io.opentracing.Tracer

internal fun Tracer.traceId(): String? {
    val activeSpan: Span = activeSpan()
    return if (activeSpan is DDSpan) {
        activeSpan.traceId.toString()
    } else null
}

internal fun Tracer.spanId(): String? {
    val activeSpan: Span = activeSpan()
    return if (activeSpan is DDSpan) {
        activeSpan.spanId.toString()
    } else null
}
