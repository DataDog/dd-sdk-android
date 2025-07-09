/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit

/**
 * Logs a throwable and associates it with the current [DatadogSpan].
 *
 * @param throwable The throwable containing error details to be logged with the span.
 */
fun DatadogSpan.logThrowable(throwable: Throwable) {
    DatadogTracingInternalToolkit.spanLogger.log(throwable, this)
}

/**
 * Logs an error message and associates it with the current [DatadogSpan].
 *
 * @param message The error message to log.
 */
fun DatadogSpan.logErrorMessage(message: String) {
    DatadogTracingInternalToolkit.spanLogger.logErrorMessage(message, this)
}

/**
 * Logs a message associated with the current [DatadogSpan].
 *
 * @param message The log message to be associated with the span.
 */
fun DatadogSpan.logMessage(message: String) {
    DatadogTracingInternalToolkit.spanLogger.log(message, this)
}

/**
 * Logs a set of attributes and associates them with the current [DatadogSpan].
 *
 * @param attributes A map containing key-value pairs of attributes to be logged.
 *                   These attributes provide additional context or metadata for the span.
 */
fun DatadogSpan.logAttributes(attributes: Map<String, Any>) {
    DatadogTracingInternalToolkit.spanLogger.log(attributes, this)
}

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
    val tracer = GlobalDatadogTracerHolder.get()

    val span = tracer.buildSpan(operationName)
        .withParentSpan(parentSpan)
        .start()

    val scope = if (activate) tracer.activateSpan(span) else null

    return try {
        span.block()
    } catch (e: Throwable) {
        DatadogTracingInternalToolkit.spanLogger.log(e, span)
        throw e
    } finally {
        span.finish()
        scope?.close()
    }
}
