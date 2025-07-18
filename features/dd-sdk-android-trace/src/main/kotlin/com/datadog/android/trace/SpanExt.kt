/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.api.span.DatadogSpan

/**
 * Wraps the provided lambda within a [DatadogSpan].
 * @param T the type returned by the lambda
 * @param operationName the name of the [DatadogSpan] created around the lambda
 * @param parentSpan the parent [DatadogSpan] (default is `null`)
 * @param activate whether the created [DatadogSpan] should be made active for the current thread
 * (default is `true`)
 * @param block the lambda function traced by this newly created [DatadogSpan]
 *
 */
@SuppressWarnings("TooGenericExceptionCaught")
inline fun <T : Any?> withinSpan(
    operationName: String,
    parentSpan: DatadogSpan? = null,
    activate: Boolean = true,
    block: DatadogSpan.() -> T
): T {
    val tracer = GlobalDatadogTracer.get()

    val span = tracer.buildSpan(operationName)
        .withParentSpan(parentSpan)
        .start()

    val scope = if (activate) tracer.activateSpan(span) else null

    return try {
        span.block()
    } catch (e: Throwable) {
        span.logThrowable(e)
        throw e
    } finally {
        span.finish()
        scope?.close()
    }
}
