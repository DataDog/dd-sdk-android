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
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.api.span.NoOpDatadogSpanLogger
import com.datadog.android.trace.api.trace.DatadogTraceIdFactory
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.api.tracer.DatadogTracerSampler
import com.datadog.android.trace.api.tracer.NoOpDatadogTracerBuilder
import com.datadog.android.trace.impl.internal.DatadogSpanIdConverterAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanLoggerAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanWriterWrapper
import com.datadog.android.trace.impl.internal.DatadogTraceIdFactoryAdapter
import com.datadog.android.trace.impl.internal.DatadogTracerBuilderAdapter
import com.datadog.android.trace.impl.internal.DatadogTracerBuilderAdapter.Companion.DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE
import com.datadog.android.trace.impl.internal.DatadogTracerSamplerWrapper
import com.datadog.trace.common.sampling.AllSampler
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.common.writer.Writer

@InternalApi
object DatadogTracing {
    @JvmField
    val spanIdConverter: DatadogSpanIdConverter = DatadogSpanIdConverterAdapter

    @JvmField
    val traceIdFactory: DatadogTraceIdFactory = DatadogTraceIdFactoryAdapter

    val spanLogger: DatadogSpanLogger
        get() = spanLoggerProvider ?: Datadog.getInstance()
            .tryCastTo<FeatureSdkCore>()
            ?.let(::DatadogSpanLoggerAdapter)
            ?: NoOpDatadogSpanLogger()

    @VisibleForTesting
    internal var builderProvider: DatadogTracerBuilder? = null

    @VisibleForTesting
    internal var spanLoggerProvider: DatadogSpanLogger? = null

    fun newTracerBuilder(sdkCore: SdkCore?): DatadogTracerBuilder {
        if (builderProvider != null) {
            return builderProvider as DatadogTracerBuilder
        }
        val internalLogger = (sdkCore as? FeatureSdkCore)?.internalLogger ?: return NoOpDatadogTracerBuilder()
        val tracingFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)?.unwrap<Feature>()
        val internalCoreWriterProvider = tracingFeature as? InternalCoreWriterProvider
        val writer = internalCoreWriterProvider?.getCoreTracerWriter()
        if (tracingFeature == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { Errors.TRACING_NOT_ENABLED_ERROR_MESSAGE }
            )
            return NoOpDatadogTracerBuilder()
        }
        if (internalCoreWriterProvider == null) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { Errors.WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE }
            )
            return NoOpDatadogTracerBuilder()
        }

        if (sdkCore.service.isEmpty()) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE }
            )
        }

        return DatadogTracerBuilderAdapter(internalLogger, writer, sdkCore.service)
    }

    fun newSampler(): DatadogTracerSampler {
        return DatadogTracerSamplerWrapper(AllSampler())
    }

    fun wrapWriter(writerDelegate: Writer?): DatadogSpanWriter {
        return DatadogSpanWriterWrapper(writerDelegate ?: NoOpWriter())
    }

    internal object Errors {
        const val TRACING_NOT_ENABLED_ERROR_MESSAGE =
            "You're trying to create an DatadogTracer instance, " +
                "but either the SDK was not initialized or the Tracing feature was " +
                "not registered. No tracing data will be sent."

        const val WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE =
            "The Tracing feature is not implementing the InternalCoreWriterProvider interface." +
                " No tracing data will be sent."
    }
}
