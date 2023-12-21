/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration

import com.datadog.android.core.internal.utils.toHexString
import io.opentracing.Span
import io.opentracing.SpanContext

/**
 * Returns the span's traceId in hex format.
 * The [SpanContext.toTraceId] method returns a string in decimal format,
 * which doesn't match what we send in our events
 */
fun Span.traceIdAsHexString(): String {
    return context().toTraceId().toLong().toHexString()
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
 * Returns the span's traceId in hex format.
 * The [SpanContext.toTraceId] method returns a string in decimal format,
 * which doesn't match what we send in our events
 */
fun Span.traceIdAsLong(): Long {
    return context().toTraceId().toLong()
}

/**
 * Returns the span's spanId in as Long.
 * The [SpanContext.toSpanId] method returns a string in decimal format,
 * which doesn't match what we send in our events
 */
fun Span.spanIdAsLong(): Long {
    return context().toSpanId().toLong()
}
