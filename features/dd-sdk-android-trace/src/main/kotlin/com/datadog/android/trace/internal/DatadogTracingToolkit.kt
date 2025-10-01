/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.trace.core.propagation.Baggage

/**
 * For library usage only.
 * Provides implementation for specific interfaces to dependent modules
 */
@InternalApi
object DatadogTracingToolkit {
    /**
     * Provides a mechanism for converting Datadog span IDs between decimal and hexadecimal representations.
     *
     * This converter is utilized to ensure span ID consistency and proper formatting for distributed tracing
     * when working with the Datadog SDK.
     */
    @JvmField
    val spanIdConverter: DatadogSpanIdConverter = DatadogSpanIdConverter()

    /**
     * Providing helper function to extract [DatadogSpanContext] from tracing context added by addParentSpan method.

     * This property is intended for internal usage only and should not
     * be altered externally.
     */
    var propagationHelper: DatadogPropagationHelper = DatadogPropagationHelper()
        internal set

    internal var testBuilderProvider: DatadogTracerBuilder? = null

    /**
     * Sets the tracing sampling priority if it is necessary.
     */
    fun setTracingSamplingPriorityIfNecessary(context: DatadogSpanContext) {
        (context as? DatadogSpanContextAdapter)?.setTracingSamplingPriorityIfNecessary()
    }

    /**
     * Enables 128-bit trace ID generation for the provided Datadog tracer builder.
     */
    fun setTraceId128BitGenerationEnabled(builder: DatadogTracerBuilder): DatadogTracerBuilder {
        (builder as? DatadogTracerBuilderAdapter)?.setTraceId128BitGenerationEnabled(true)
        return builder
    }

    /**
     * Enables compatibility mode with SDK v2 for sampling factory strategy.
     */
    fun setSdkV2Compatible(builder: DatadogTracerBuilder): DatadogTracerBuilder {
        (builder as? DatadogTracerBuilderAdapter)?.setSdkV2Compatible()
        return builder
    }

    /**
     * Associates a throwable with the current span, marking it as an error.
     * Note that error flag will be set only if priority is higher than the current one.
     *
     * @param span The span to associate the throwable with.
     * @param throwable The throwable to associate with the current span.
     * @param errorPriority The priority level of the error, represented as a byte.
     */
    @JvmStatic // this method is called from OTel code, written in java
    fun addThrowable(span: DatadogSpan, throwable: Throwable, errorPriority: Byte) {
        (span as? DatadogSpanAdapter)?.addThrowable(throwable, errorPriority)
    }

    /**
     * Activates the provided span within the current context of the tracer.
     * If `asyncPropagating` is set to true, the span is propagated asynchronously.
     * Once activated, the span becomes the currently active span until it is explicitly deactivated.
     *
     * @param tracer The tracer instance to be used for activation.
     * @param span The span to be activated. Represents the logical unit of work being traced.
     * @param asyncPropagating If true, this context will propagate across async boundaries.
     * @return An instance of [DatadogScope] representing the activated scope.
     */
    @JvmStatic // this method is called from OTel code, written in java
    fun activateSpan(tracer: DatadogTracer, span: DatadogSpan, asyncPropagating: Boolean): DatadogScope? {
        return (tracer as? DatadogTracerAdapter)?.activateSpan(span, asyncPropagating)
    }

    /**
     * Merges two baggage headers into a single string representation. The [oldHeader] represents the
     * previously existing baggage header, and the [newHeader] represents the new baggage information
     * to be merged with the old one.
     *
     * @param oldHeader The existing baggage header, which may be null.
     * @param newHeader The new baggage header to be merged, must not be null.
     * @return A string representation of the merged baggage.
     */
    fun mergeBaggage(oldHeader: String?, newHeader: String): String {
        return Baggage.from(oldHeader)
            .mergeWith(Baggage.from(newHeader))
            .toString()
    }
}
