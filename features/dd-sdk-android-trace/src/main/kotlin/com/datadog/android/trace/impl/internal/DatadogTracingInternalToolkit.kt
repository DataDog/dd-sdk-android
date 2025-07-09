/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.Datadog
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.span.DatadogSpanLogger
import com.datadog.android.trace.api.span.NoOpDatadogSpanLogger
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder

/**
 * For library usage only.
 * Provides implementation for specific interfaces to dependant modules
 */
@InternalApi
object DatadogTracingInternalToolkit {
    /**
     * Provides a mechanism for converting Datadog span IDs between decimal and hexadecimal representations.
     *
     * This converter is utilized to ensure span ID consistency and proper formatting for distributed tracing
     * when working with the Datadog SDK.
     */
    @JvmField
    val spanIdConverter: DatadogSpanIdConverter = DatadogSpanIdConverterAdapter

    /**
     * A factory instance for creating and working with [com.datadog.android.trace.api.trace.DatadogTraceId] objects.
     */
    @JvmField
    val traceIdConverter: DatadogTraceIdConverter = DatadogTraceIdConverterAdapter

    /**
     * Provides an instance of [DatadogSpanLogger] for logging span-related messages, errors,
     * and attributes. Selects the appropriate logger implementation based on the available context.
     */
    var spanLogger: DatadogSpanLogger
        internal set(value) {
            spanLoggerNullable = value
        }
        get() = spanLoggerNullable
            ?: (Datadog.getInstance() as? FeatureSdkCore)?.let(::DatadogSpanLoggerAdapter)
            ?: NoOpDatadogSpanLogger()

    internal var spanLoggerNullable: DatadogSpanLogger? = null

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

    internal object ErrorMessages {
        const val TRACING_NOT_ENABLED_ERROR_MESSAGE: String =
            "You're trying to create an DatadogTracer instance, " +
                "but either the SDK was not initialized or the Tracing feature was " +
                "not registered. No tracing data will be sent."

        const val WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE: String =
            "The Tracing feature is not implementing the InternalCoreWriterProvider interface." +
                " No tracing data will be sent."

        const val DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE: String =
            "Default service name is missing during DatadogTracerBuilder creation, did you initialize SDK?"

        fun buildWrongWrapperMessage(cls: Class<*>?): String {
            return "You're trying to create an DatadogTracer instance, " +
                "but provided ${cls?.canonicalName} writer wrapper is not supported."
        }
    }
}
