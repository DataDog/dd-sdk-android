/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal.otel

import com.datadog.android.okhttp.TraceContext
import com.datadog.opentracing.propagation.ExtractedContext
import io.opentracing.SpanContext
import java.math.BigInteger

private const val BASE_16_RADIX = 16

internal fun TraceContext.toOpenTracingContext(): SpanContext {
    val traceIdAsBigInteger = parseToBigInteger(traceId)
    val spanIdAsBigInteger = parseToBigInteger(spanId)
    return ExtractedContext(
        traceIdAsBigInteger,
        spanIdAsBigInteger,
        samplingPriority,
        null,
        emptyMap(),
        emptyMap()
    )
}

@Suppress("SwallowedException")
private fun parseToBigInteger(value: String): BigInteger {
    // just in case but theoretically it should never happen as we are controlling the way the ID is
    // generated in the CoreTracer.
    return try {
        BigInteger(value, BASE_16_RADIX)
    } catch (e: NumberFormatException) {
        BigInteger.ZERO
    } catch (e: ArithmeticException) {
        BigInteger.ZERO
    }
}
