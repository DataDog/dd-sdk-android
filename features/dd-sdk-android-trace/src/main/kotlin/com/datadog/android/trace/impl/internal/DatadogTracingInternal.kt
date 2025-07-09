/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.span.DatadogSpanIdConverter
import com.datadog.android.trace.api.trace.DatadogTraceIdFactory

/**
 * For library usage only.
 * Provides implementation for specific interfaces to dependant modules
 */
@InternalApi
object DatadogTracingInternal {
    /**
     * Provides a mechanism for converting Datadog span IDs between decimal and hexadecimal representations.
     *
     * This converter is utilized to ensure span ID consistency and proper formatting for distributed tracing
     * when working with the Datadog SDK.
     */
    @JvmField
    @InternalApi
    val spanIdConverter: DatadogSpanIdConverter = DatadogSpanIdConverterAdapter

    /**
     * A factory instance for creating and working with [com.datadog.android.trace.api.trace.DatadogTraceId] objects.
     */
    @JvmField
    @InternalApi
    val traceIdFactory: DatadogTraceIdFactory = DatadogTraceIdFactoryAdapter

    /**
     * Sets the tracing sampling priority if it is necessary.
     */
    @InternalApi
    fun setTracingSamplingPriorityIfNecessary(context: DatadogSpanContext) {
        (context as? DatadogSpanContextAdapter)?.setTracingSamplingPriorityIfNecessary()
    }
}
