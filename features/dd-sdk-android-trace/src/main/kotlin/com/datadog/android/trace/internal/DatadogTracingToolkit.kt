/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import androidx.annotation.IntRange
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder

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

    internal var builderProvider: DatadogTracerBuilder? = null

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
     * Sets the trace rate limit. This is the maximum number of traces per second that will be
     * accepted. Please note that this property is used in conjunction with the sample rate. If no sample rate
     * is provided this property and its related logic will be ignored.
     * @param builder the tracer builder to set the trace rate limit for
     * @param traceRateLimit the trace rate limit as a value between 1 and Int.MAX_VALUE (default is Int.MAX_VALUE)
     */
    fun setTraceRateLimit(
        builder: DatadogTracerBuilder,
        @IntRange(from = 1, to = Int.MAX_VALUE.toLong()) traceRateLimit: Int
    ): DatadogTracerBuilder {
        (builder as? DatadogTracerBuilderAdapter)?.withTraceRateLimit(traceRateLimit)
        return builder
    }
}
