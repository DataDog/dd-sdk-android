/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight.internal

import com.datadog.android.trace.AndroidTracer
import io.opentracing.Span
import io.opentracing.util.GlobalTracer

@Suppress("ThrowingInternalException", "TooGenericExceptionCaught")
internal inline fun <T : Any?> withinSpan(
    operationName: String,
    parentSpan: Span? = null,
    block: Span.() -> T
): T {
    val tracer = GlobalTracer.get()

    val span = tracer.buildSpan(operationName)
        .asChildOf(parentSpan)
        .start()

    val scope = tracer.activateSpan(span)

    return try {
        span.block()
    } catch (e: Throwable) {
        AndroidTracer.logThrowable(span, e)
        throw e
    } finally {
        span.finish()
        scope.close()
    }
}
