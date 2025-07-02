/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.propagation

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.span.NoOpDatadogSpanContext

/**
 * Provides an interface for propagating Datadog spans and span contexts across process boundaries or systems.
 */
@SuppressWarnings("OutdatedDocumentation")
interface DatadogPropagation {
    /**
     * Injects the span's context into a given carrier using a specified setter function.
     * This enables the propagation of trace and span information across different systems
     * or process boundaries.
     *
     * @param span The DatadogSpan containing the trace and span information to be injected.
     * @param carrier The carrier object where the span context will be injected.
     * @param setter A setter function that defines how to insert key-value pairs into the carrier.
     *               It accepts the carrier, a key, and a value as parameters.
     */
    fun <C> inject(span: DatadogSpan, carrier: C, setter: (carrier: C, key: String, value: String) -> Unit)

    /**
     * Injects the span context into a specified carrier using the provided setter function.
     * This method facilitates the propagation of trace information across process boundaries
     * or systems by embedding relevant span context data into the carrier.
     *
     * @param context The DatadogSpanContext containing the trace and span information to inject.
     * @param carrier The carrier object where the trace information will be injected.
     * @param setter A function used to set key-value pairs into the carrier. It takes the carrier,
     *               a key, and a value as parameters.
     */
    fun <C> inject(
        context: DatadogSpanContext,
        carrier: C,
        setter: (carrier: C, key: String, value: String) -> Unit
    )

    /**
     * Extracts a `DatadogSpanContext` from the provided carrier using the specified getter function.
     * This method is used to propagate Datadog span context information across systems by retrieving
     * trace and span information from the carrier.
     *
     * @param C The type of the carrier containing the span context information.
     * @param carrier The carrier object from which the span context will be extracted.
     * @param getter A function that retrieves key-value pairs from the carrier, using a classifier function
     *               to match desired key-value pairs.
     *               The classifier function takes two strings (key and value) and returns a boolean
     *               indicating whether the key-value pair belongs to the span context.
     * @return The extracted `DatadogSpanContext` if one could be retrieved, or null otherwise.
     */
    fun <C> extract(
        carrier: C,
        getter: (carrier: C, classifier: (String, String) -> Boolean) -> Unit
    ): DatadogSpanContext?

    /**
     * Determines if the provided DatadogSpanContext represents an extracted context.
     *
     * @param context The DatadogSpanContext to be evaluated.
     * @return True if the context is identified as an extracted context, otherwise false.
     */
    fun isExtractedContext(context: DatadogSpanContext): Boolean

    /**
     * Creates a `DatadogSpanContext` object that represents an extracted context from the given parameters.
     *
     * @param traceId The unique identifier for the trace.
     * @param spanId The unique identifier for the span.
     * @param samplingPriority The sampling priority value for determining the trace's sampling behavior.
     * @return A `DatadogSpanContext` instance containing the extracted context.
     */
    fun createExtractedContext(traceId: String, spanId: String, samplingPriority: Int): DatadogSpanContext
}

/**
 * A no-operation implementation of the `DatadogPropagation` interface.
 *
 * This implementation is intended as a placeholder making possible to create other NoOp.* classes.
 */
// TODO RUM-10573 - replace with @NoOpImplementation when method-level generics will be supported in noopfactory
class NoOpDatadogPropagation : DatadogPropagation {
    override fun <C> inject(
        span: DatadogSpan,
        carrier: C,
        setter: (carrier: C, key: String, value: String) -> Unit
    ) = Unit // Do nothing

    override fun <C> inject(
        context: DatadogSpanContext,
        carrier: C,
        setter: (carrier: C, key: String, value: String) -> Unit
    ) = Unit // Do nothing

    override fun <C> extract(
        carrier: C,
        getter: (carrier: C, classifier: (String, String) -> Boolean) -> Unit
    ): DatadogSpanContext? = null // Do nothing

    override fun isExtractedContext(context: DatadogSpanContext) = false // Do nothing

    override fun createExtractedContext(
        traceId: String,
        spanId: String,
        samplingPriority: Int
    ): DatadogSpanContext = NoOpDatadogSpanContext()
}
