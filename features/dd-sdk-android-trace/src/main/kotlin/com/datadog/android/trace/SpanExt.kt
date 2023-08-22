/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import io.opentracing.Span
import io.opentracing.util.GlobalTracer

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
 * The error message will be added to this [Span] as a standard Error Tag.
 * @param message the error message you want to attach.
 */
fun Span.setError(message: String) {
    AndroidTracer.logErrorMessage(this, message)
}

/**
 * Wraps the provided lambda within a [Span].
 * @param T the type returned by the lambda
 * @param operationName the name of the [Span] created around the lambda
 * @param parentSpan the parent [Span] (default is `null`)
 * @param activate whether the created [Span] should be made active for the current thread
 * (default is `true`)
 * @param block the lambda function traced by this newly created [Span]
 *
 */
@SuppressWarnings("TooGenericExceptionCaught")
inline fun <T : Any?> withinSpan(
    operationName: String,
    parentSpan: Span? = null,
    activate: Boolean = true,
    block: Span.() -> T
): T {
    val tracer = GlobalTracer.get()

    val span = tracer.buildSpan(operationName)
        .asChildOf(parentSpan)
        .start()

    val scope = if (activate) tracer.activateSpan(span) else null

    return try {
        span.block()
    } catch (e: Throwable) {
        span.setError(e)
        throw e
    } finally {
        span.finish()
        scope?.close()
    }
}
