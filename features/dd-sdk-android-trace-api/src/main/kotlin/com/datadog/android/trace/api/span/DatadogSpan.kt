/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.span

import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.tools.annotation.NoOpImplementation

/**
 * Represents an interface for a Datadog span, which encapsulates information about a single span within a trace.
 * This includes span metadata, error handling, timing, and tag/metric management.
 */
@NoOpImplementation
@SuppressWarnings("TooManyFunctions")
interface DatadogSpan {
    /**
     * Indicates whether the current span is marked as an error.
     */
    var isError: Boolean?

    /**
     * Indicates whether the current span is the root span in a trace.
     */
    val isRootSpan: Boolean

    /**
     * Represents the sampling priority of the span, which determines if the span should be
     * included or excluded from trace collection and analysis.
     */
    val samplingPriority: Int?

    /**
     * Represents the unique identifier for a trace in the Datadog tracing system.
     */
    val traceId: DatadogTraceId

    /**
     * Represents the parent span's unique identifier in a trace.
     */
    val parentSpanId: Long?

    /**
     * Represents the name of the resource associated with the span.
     */
    var resourceName: String?

    /**
     * Defines the name of the service associated with this span.
     */
    var serviceName: String

    /**
     * The name of the operation represented by the span.
     */
    var operationName: String

    /**
     * Represents the duration of a span in nanoseconds.
     */
    val durationNano: Long

    /**
     * Represents the start time of the span in nanoseconds.
     */
    val startTimeNanos: Long

    /**
     * Refers to the local root span within a trace hierarchy.
     */
    val localRootSpan: DatadogSpan?

    /**
     * Retrieves the context associated with this Datadog span.
     *
     * @return The [DatadogSpanContext] containing trace and span-specific information such as identifiers, sampling priority, and tags.
     */
    fun context(): DatadogSpanContext

    /**
     * Marks the end of this span and captures its duration.
     * This method should be called once the operation represented by the span
     * is completed to ensure proper timing and resource tracking.
     */
    fun finish()

    /**
     * Marks the end of this span and sets its finish time.
     * This method should be used when the operation represented by the span is completed,
     * and the exact finish timestamp in microseconds needs to be explicitly provided.
     *
     * @param finishMicros The finish time of the span, provided in microseconds since the epoch.
     */
    fun finish(finishMicros: Long)

    /**
     * Marks the current span for removal, indicating it should no longer be processed or considered active.
     * This method can be used to discard spans that are no longer relevant or should not be reported.
     */
    fun drop()

    /**
     * Sets the error message for the current span. This message provides a description
     * of the error that occurred during the span's operation.
     *
     * @param message The error message to be associated with the span.
     */
    fun setErrorMessage(message: String?)

    /**
     * Associates a throwable with the current span, marking it as an error
     * and capturing the provided throwable for additional context.
     *
     * @param throwable The throwable to associate with the current span.
     */
    fun addThrowable(throwable: Throwable)

    /**
     * Associates a throwable with the current span, marking it as an error
     * and capturing the provided throwable for additional context with a specified error priority.
     *
     * @param throwable The throwable to associate with the current span.
     * @param errorPriority The priority level of the error, represented as a byte.
     */
    fun addThrowable(throwable: Throwable, errorPriority: Byte)

    /**
     * Associates a tag with the specified value for the current span.
     *
     * @param tag The name of the tag to associate with the span.
     * @param value The value to associate with the specified tag.
     */
    fun setTag(tag: String?, value: String?)

    /**
     * Associates a tag with a boolean value for the current span.
     *
     * @param tag The name of the tag to associate with the span.
     * @param value The boolean value to associate with the specified tag.
     */
    fun setTag(tag: String?, value: Boolean)

    /**
     * Associates a tag with a numerical value for the current span.
     *
     * @param tag The name of the tag to associate with the span.
     * @param value The numerical value to associate with the specified tag.
     */
    fun setTag(tag: String?, value: Number?)

    /**
     * Associates a tag with a specified value for the current span.
     *
     * @param tag The name of the tag to associate with the span.
     * @param value The value to associate with the specified tag.
     */
    fun setTag(tag: String?, value: Any?)

    /**
     * Retrieves the value associated with the specified tag for the current span.
     *
     * @param tag The name of the tag whose value is to be retrieved.
     * @return The value associated with the specified tag.
     */
    fun getTag(tag: String?): Any?

    /**
     * Sets a metric for the current span with the specified key and value.
     *
     * @param key The name of the metric to be associated with the span.
     * @param value The value of the metric to be set for the specified key.
     */
    fun setMetric(key: String, value: Int)

    /**
     * Logs a throwable and associates it with the current [DatadogSpan].
     *
     * @param throwable The throwable containing error details to be logged with the span.
     */
    fun logThrowable(throwable: Throwable)

    /**
     * Logs an error message and associates it with the current [DatadogSpan].
     *
     * @param message The error message to log.
     */
    fun logErrorMessage(message: String)

    /**
     * Logs a message associated with the current [DatadogSpan].
     *
     * @param message The log message to be associated with the span.
     */
    fun logMessage(message: String)

    /**
     * Logs a set of attributes and associates them with the current [DatadogSpan].
     *
     * @param attributes A map containing key-value pairs of attributes to be logged.
     *                   These attributes provide additional context or metadata for the span.
     */
    fun logAttributes(attributes: Map<String, Any>)
}
