/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.otel

import com.datadog.android.okhttp.internal.OkHttpHttpRequestInfoModifier
import com.datadog.android.trace.api.DatadogTracingConstants
import com.datadog.android.trace.internal.DatadogTracingToolkit
import com.datadog.opentelemetry.trace.OtelSpan
import io.opentelemetry.api.trace.Span
import okhttp3.Request

/**
 * Add the current span context as the parent span for the distributed trace created around the Request.
 * @param span the parent span to add to the request.
 * @return the modified Request.Builder instance
 */
fun Request.Builder.addParentSpan(span: Span): Request.Builder = apply {
    // very fragile and assumes that Datadog Tracer is used
    // we need to trigger sampling decision at this point, because we are doing context propagation out of OpenTelemetry
    val modifier = OkHttpHttpRequestInfoModifier(this)
    if (span is OtelSpan) {
        DatadogTracingToolkit.setTracingSamplingPriorityIfNecessary(span.datadogSpanContext)
        DatadogTracingToolkit.propagationHelper.setTraceContext(
            modifier,
            span.spanContext.traceId,
            span.spanContext.spanId,
            span.datadogSpanContext.samplingPriority
        )
    } else {
        DatadogTracingToolkit.propagationHelper.setTraceContext(
            modifier,
            span.spanContext.traceId,
            span.spanContext.spanId,
            if (span.spanContext.isSampled) {
                DatadogTracingConstants.PrioritySampling.USER_KEEP
            } else {
                DatadogTracingConstants.PrioritySampling.UNSET
            }
        )
    }
}
