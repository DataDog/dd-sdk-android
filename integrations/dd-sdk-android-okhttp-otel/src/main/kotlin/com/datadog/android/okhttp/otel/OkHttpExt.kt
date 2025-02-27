/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.otel

import com.datadog.android.okhttp.TraceContext
import com.datadog.legacy.trace.api.sampling.PrioritySampling
import com.datadog.opentelemetry.trace.OtelSpan
import com.datadog.trace.core.DDSpanContext
import io.opentelemetry.api.trace.Span
import okhttp3.Request

/**
 * Add the current span context as the parent span for the distributed trace created around the Request.
 * @param span the parent span to add to the request.
 * @return the modified Request.Builder instance
 */
fun Request.Builder.addParentSpan(span: Span): Request.Builder {
    // very fragile and assumes that Datadog Tracer is used
    // we need to trigger sampling decision at this point, because we are doing context propagation out of OpenTelemetry
    if (span is OtelSpan) {
        val agentSpanContext = span.agentSpanContext
        if (agentSpanContext is DDSpanContext) {
            agentSpanContext.trace.setSamplingPriorityIfNecessary()
        }
        @Suppress("UnsafeThirdPartyFunctionCall") // the context will always be a TraceContext
        tag(
            TraceContext::class.java,
            TraceContext(span.spanContext.traceId, span.spanContext.spanId, agentSpanContext.samplingPriority)
        )
    } else {
        val context = span.spanContext
        val prioritySampling = if (context.isSampled) PrioritySampling.USER_KEEP else PrioritySampling.UNSET
        @Suppress("UnsafeThirdPartyFunctionCall") // the context will always be a TraceContext
        tag(TraceContext::class.java, TraceContext(context.traceId, context.spanId, prioritySampling))
    }
    return this
}
