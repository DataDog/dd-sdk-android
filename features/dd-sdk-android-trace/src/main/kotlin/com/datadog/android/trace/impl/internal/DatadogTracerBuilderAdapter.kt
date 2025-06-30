/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.constants.DatadogTracingConstants.TracerConfig
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.api.tracer.DatadogTracerSampler
import com.datadog.trace.api.IdGenerationStrategy
import com.datadog.trace.core.CoreTracer
import java.util.Properties

internal class DatadogTracerBuilderAdapter(
    private val internalLogger: InternalLogger,
    writer: DatadogSpanWriter?,
    private val defaultServiceName: String
) : DatadogTracerBuilder {

    private var properties = Properties()
    private val delegate = CoreTracer.CoreTracerBuilder(internalLogger)
    private var serviceNameSet: Boolean = false

    init {
        if (writer is DatadogSpanWriterWrapper) {
            delegate.writer(writer.delegate)
        } else {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { MESSAGE_WRITER_NOT_PROVIDED }
            )
        }
    }

    override fun build(): DatadogTracer {
        if (!serviceNameSet && !properties.contains(TracerConfig.SERVICE_NAME)) {
            delegate.serviceName(defaultServiceName)
        }

        return DatadogTracerAdapter(delegate.build())
    }

    override fun withServiceName(serviceName: String) = apply {
        this.serviceNameSet = true
        delegate.serviceName(serviceName)
    }

    override fun withProperties(properties: Properties) = apply {
        this.properties = properties
        delegate.withProperties(properties)
    }

    override fun withTracingHeadersTypes(tracingHeadersTypes: Set<TracingHeaderType>): DatadogTracerBuilder {
        val propagationStyles = tracingHeadersTypes.joinToString(",")
        properties.setProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT, propagationStyles)
        properties.setProperty(TracerConfig.PROPAGATION_STYLE_INJECT, propagationStyles)
        return withProperties(properties)
    }

    override fun withSampler(samplerAdapter: DatadogTracerSampler?): DatadogTracerBuilder = apply {
        if (samplerAdapter is DatadogTracerSamplerWrapper) delegate.sampler(samplerAdapter.delegate)
    }

    override fun withPartialFlushMinSpans(partialFlushThreshold: Int) = apply {
        delegate.partialFlushMinSpans(partialFlushThreshold)
    }

    override fun withIdGenerationStrategy(key: String, traceId128BitGenerationEnabled: Boolean) = apply {
        delegate.idGenerationStrategy(IdGenerationStrategy.fromName(key, traceId128BitGenerationEnabled))
    }

    companion object {
        internal const val MESSAGE_WRITER_NOT_PROVIDED =
            "You're trying to create an DatadogTracerBuilder instance, " +
                    "but either the SDK was not initialized or the Tracing feature was " +
                    "not registered. No tracing data will be sent."

        internal const val DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE =
            "Default service name is missing during" +
                    " DatadogTracerBuilder creation, did you initialize SDK?"
    }
}
