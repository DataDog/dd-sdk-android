/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import androidx.annotation.VisibleForTesting
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.utils.tryCastTo
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.api.span.DatadogSpanIdConverter
import com.datadog.android.trace.api.span.DatadogSpanLogger
import com.datadog.android.trace.api.span.NoOpDatadogSpanLogger
import com.datadog.android.trace.api.trace.DatadogTraceIdFactory
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.api.tracer.NoOpDatadogTracerBuilder
import com.datadog.android.trace.impl.internal.DatadogSpanIdConverterAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanLoggerAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanWriterWrapper
import com.datadog.android.trace.impl.internal.DatadogTraceIdFactoryAdapter
import com.datadog.android.trace.impl.internal.DatadogTracerBuilderAdapter
import com.datadog.android.trace.internal.data.NoOpCoreTracerWriter

/**
 * Provides utilities and components for creating Datadog distributed tracing components in an application.
 * This includes creating new tracer builders, span loggers, and span writers. The class also
 * contains configurations and adapters required to integrate with the Datadog SDK features.
 */
@SuppressWarnings("UndocumentedPublicFunction")
object DatadogTracing {
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
    val traceIdFactory: DatadogTraceIdFactory = DatadogTraceIdFactoryAdapter

    /**
     * Provides an instance of [DatadogSpanLogger] for logging span-related messages, errors,
     * and attributes. Selects the appropriate logger implementation based on the available context.
     */
    @InternalApi
    val spanLogger: DatadogSpanLogger
        get() = spanLoggerProvider ?: Datadog.getInstance()
            .tryCastTo<FeatureSdkCore>()
            ?.let(::DatadogSpanLoggerAdapter)
            ?: NoOpDatadogSpanLogger()

    /**
     * Creates and returns a new instance of [DatadogTracerBuilder]. This method initializes and configures the
     * tracer builder based on the provided SDK core instance and its setup. If certain required configurations
     * are missing, a no-operation tracer builder is returned instead.
     *
     * @param sdkCore An instance of [SdkCore], which provides configuration and features needed for initializing
     * the tracer builder.
     * @return A configured instance of [DatadogTracerBuilder], or a no-operation implementation if the necessary
     * configurations or features are unavailable.
     */
    fun newTracerBuilder(sdkCore: SdkCore): DatadogTracerBuilder = when {
        builderProvider != null -> builderProvider as DatadogTracerBuilder
        sdkCore !is FeatureSdkCore -> NoOpDatadogTracerBuilder()
        else -> {
            val internalLogger = sdkCore.internalLogger
            val tracingFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)?.unwrap<Feature>()
            if (tracingFeature == null) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { Errors.TRACING_NOT_ENABLED_ERROR_MESSAGE }
                )
            }

            val internalCoreWriterProvider = tracingFeature as? InternalCoreWriterProvider
            if (internalCoreWriterProvider == null) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { Errors.WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE }
                )
            }

            if (sdkCore.service.isEmpty()) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { Errors.DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE }
                )
            }

            DatadogTracerBuilderAdapter(
                internalLogger,
                internalCoreWriterProvider?.getCoreTracerWriter()
                    ?: DatadogSpanWriterWrapper(NoOpCoreTracerWriter()),
                sdkCore.service
            )
        }
    }

    @VisibleForTesting
    internal var builderProvider: DatadogTracerBuilder? = null

    @VisibleForTesting
    internal var spanLoggerProvider: DatadogSpanLogger? = null

    internal object Errors {
        const val TRACING_NOT_ENABLED_ERROR_MESSAGE =
            "You're trying to create an DatadogTracer instance, " +
                "but either the SDK was not initialized or the Tracing feature was " +
                "not registered. No tracing data will be sent."

        const val WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE =
            "The Tracing feature is not implementing the InternalCoreWriterProvider interface." +
                " No tracing data will be sent."

        const val DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE =
            "Default service name is missing during DatadogTracerBuilder creation, did you initialize SDK?"
    }
}
