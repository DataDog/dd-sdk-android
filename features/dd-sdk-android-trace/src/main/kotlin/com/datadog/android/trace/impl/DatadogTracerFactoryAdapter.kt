/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.utils.tryCastTo
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerFactory
import com.datadog.trace.api.config.TracerConfig
import com.datadog.trace.common.sampling.AllSampler
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.core.CoreTracer
import java.util.Properties

class DatadogTracerFactoryAdapter(private val sdkCore: SdkCore) : DatadogTracerFactory {
    override fun create(tracingHeaderTypes: Set<TracingHeaderType>): DatadogTracer {
        val featuredSdkCore = sdkCore as FeatureSdkCore
        val writer = featuredSdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
            ?.unwrap<Feature>()
            ?.tryCastTo<com.datadog.android.trace.InternalCoreWriterProvider>()
            ?.getCoreTracerWriter()
            ?: run {
                featuredSdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.MAINTAINER,
                    {
                        "The Tracing feature is not implementing the InternalCoreWriterProvider interface. " +
                                "No tracing data will be sent."
                    }
                )
                NoOpWriter()
            }

        val tracer = CoreTracer.CoreTracerBuilder(featuredSdkCore.internalLogger)
            .withProperties(
                Properties().apply {
                    val propagationStyles = tracingHeaderTypes.joinToString(",")
                    setProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT, propagationStyles)
                    setProperty(TracerConfig.PROPAGATION_STYLE_INJECT, propagationStyles)
                    setProperty(TracerConfig.URL_AS_RESOURCE_NAME, "false")
                }
            )
            .writer(writer)
            .sampler(AllSampler())
            .build()

        return DatadogTracerAdapter(tracer)
    }
}
