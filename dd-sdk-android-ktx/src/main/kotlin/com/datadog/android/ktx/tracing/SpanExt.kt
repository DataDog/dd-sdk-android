package com.datadog.android.ktx.tracing

import com.datadog.android.tracing.AndroidTracer
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
 * @param message the error message you want to attach
 */
fun Span.setError(message: String) {
    AndroidTracer.logErrorMessage(this, message)
}

/**
 * Wraps the provided lambda within a [Span]
 *
 */
inline fun <T : Any> withinSpan(
    operationName: String,
    parentSpan: Span? = null,
    block: Span.() -> T?
): T? {
    val tracer = GlobalTracer.get()

    val span = tracer.buildSpan(operationName)
        .asChildOf(parentSpan)
        .start()

    return try {
        val result = span.block()
        result
    } catch (e: Throwable) {
        span.setError(e)
        throw e
    } finally {
        span.finish()
    }
}
