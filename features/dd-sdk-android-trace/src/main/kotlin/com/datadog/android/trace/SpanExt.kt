/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.impl.DatadogTracing
import io.opentracing.Span

/**
 * Helper method to attach a Throwable to this [Span].
 * The Throwable information (class name, message and stacktrace) will be added to
 * this [Span] as standard Error Tags.
 * @param throwable the [Throwable] you wan to log
 */
fun Span.setError(throwable: Throwable) {
    AndroidTracer.logThrowable(this, throwable)
}

/**
 * Helper method to attach an error message to this [Span].
 * The error message will be logged with ERROR status and can be seen in logs attached to the span.
 * @param message the error message you want to attach.
 */
fun Span.setError(message: String) {
    AndroidTracer.logErrorMessage(this, message)
}

fun DatadogSpan.log(message: String) {
    DatadogTracing.spanLogger.log(message, this)
}

fun DatadogSpan.log(attributes: Map<String, Any>) {
    DatadogTracing.spanLogger.log(attributes, this)
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
        span.addThrowable(e)
        throw e
    } finally {
        span.finish()
        scope?.close()
    }
}