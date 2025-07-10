/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.api.tracer.NoOpDatadogTracerBuilder
import com.datadog.android.trace.impl.internal.DatadogSpanWriterWrapper
import com.datadog.android.trace.impl.internal.DatadogTracerBuilderAdapter
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit.ErrorMessages.DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit.ErrorMessages.TRACING_NOT_ENABLED_ERROR_MESSAGE
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit.ErrorMessages.WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE
import com.datadog.android.trace.impl.internal.DatadogTracingInternalToolkit.ErrorMessages.buildWrongWrapperMessage
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.core.CoreTracer

/**
 * Object responsible for providing tracing capabilities in the Datadog SDK.
 */
@SuppressWarnings("UndocumentedPublicFunction")
object DatadogTracing {
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
    fun newTracerBuilder(sdkCore: SdkCore = Datadog.getInstance()): DatadogTracerBuilder = when {
        DatadogTracingInternalToolkit.builderProvider != null -> {
            DatadogTracingInternalToolkit.builderProvider as DatadogTracerBuilder
        }
        sdkCore !is FeatureSdkCore -> {
            NoOpDatadogTracerBuilder()
        }
        else -> {
            val internalLogger = sdkCore.internalLogger
            val tracingFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)?.unwrap<Feature>()
            val internalCoreWriterProvider = tracingFeature as? InternalCoreWriterProvider
            val writer = (internalCoreWriterProvider?.getCoreTracerWriter() as? DatadogSpanWriterWrapper)?.delegate

            when {
                null == tracingFeature -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { TRACING_NOT_ENABLED_ERROR_MESSAGE }
                )

                null == internalCoreWriterProvider -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    { WRITER_PROVIDER_INTERFACE_NOT_IMPLEMENTED_ERROR_MESSAGE }
                )

                null == writer -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { buildWrongWrapperMessage(internalCoreWriterProvider.getCoreTracerWriter().javaClass) }
                )

                sdkCore.service.isEmpty() -> internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE }
                )
            }

            DatadogTracerBuilderAdapter(
                sdkCore = sdkCore,
                serviceName = sdkCore.service,
                delegate = CoreTracer.CoreTracerBuilder(sdkCore.internalLogger).writer(writer ?: NoOpWriter())
            )
        }
    }
}
