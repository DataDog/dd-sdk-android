/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sqldelight.internal

import com.datadog.android.trace.GlobalDatadogTracerHolder
import com.datadog.android.trace.api.span.DatadogSpan

@Suppress("ThrowingInternalException", "TooGenericExceptionCaught")
internal inline fun <T : Any?> withinSpan(
    operationName: String,
    parentSpan: DatadogSpan? = null,
    block: DatadogSpan.() -> T
): T {
    val tracer = GlobalDatadogTracerHolder.get()

    val span = tracer.buildSpan(operationName)
        .withParentSpan(parentSpan)
        .start()

    val scope = tracer.activateSpan(span)

    return try {
        span.block()
    } catch (e: Throwable) {
        span.addThrowable(e)
        throw e
    } finally {
        span.finish()
        scope?.close()
    }
}
