/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.opentracing

import com.datadog.android.core.internal.utils.toHexString
import com.datadog.opentracing.DDSpan
import io.opentracing.Span
import io.opentracing.SpanContext

/**
 * Returns the span's less significant trace id in hex format (the last 64 bits from the 128 bits trace id)
 */
fun Span.leastSignificantTraceId(): String {
    return (this as? DDSpan)?.traceId?.toString(16)?.padStart(32, '0')?.takeLast(16) ?: ""
}

/**
 * Returns the span's most significant trace id in hex format (the first 64 bits from the 128 bits trace id)
 */
fun Span.mostSignificantTraceId(): String {
    return (this as? DDSpan)?.traceId?.toString(16)?.padStart(32, '0')?.take(16) ?: ""
}

/**
 * Returns the span's spanId in hex format.
 * The [SpanContext.toSpanId] method returns a string in decimal format,
 * which doesn't match what we send in our events
 */
fun Span.spanIdAsHexString(): String {
    return context().toSpanId().toLong().toHexString()
}

/**
 * Returns the span's spanId in as Long.
 * The [SpanContext.toSpanId] method returns a string in decimal format,
 * which doesn't match what we send in our events
 */
fun Span.spanIdAsLong(): Long {
    return context().toSpanId().toLong()
}
