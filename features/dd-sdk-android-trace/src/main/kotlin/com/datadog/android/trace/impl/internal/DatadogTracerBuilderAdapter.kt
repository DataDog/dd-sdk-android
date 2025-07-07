/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.TracerConfig
import com.datadog.android.trace.api.sampling.DatadogTracerSampler
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.trace.api.IdGenerationStrategy
import com.datadog.trace.core.CoreTracer
import java.util.Properties

internal class DatadogTracerBuilderAdapter(
    private val sdkCore: FeatureSdkCore,
    writer: DatadogSpanWriter?,
    defaultServiceName: String
) : DatadogTracerBuilder {

    private var properties: Properties? = null
    private val internalLogger: InternalLogger
        get() = sdkCore.internalLogger

    @VisibleForTesting
    internal val delegate = CoreTracer.CoreTracerBuilder(internalLogger)
    private var serviceName: String = defaultServiceName
    private var sampleRate: Double? = null
    private var bundleWithRumEnabled: Boolean = true
    private var traceRateLimit = Int.MAX_VALUE
    private var partialFlushThreshold = DEFAULT_PARTIAL_MIN_FLUSH
    private val globalTags: MutableMap<String, String> = mutableMapOf()
    private var tracingHeadersTypes: Set<TracingHeaderType> = setOf(
        TracingHeaderType.DATADOG,
        TracingHeaderType.TRACECONTEXT
    )

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
        val coreTracer = delegate.withProperties(properties ?: properties()).build()

        return DatadogTracerAdapter(
            sdkCore,
            coreTracer,
            bundleWithRumEnabled
        )
    }

    override fun withServiceName(serviceName: String) = apply {
        this.serviceName = serviceName
    }

    override fun withProperties(properties: Properties) = apply {
        this.properties = properties
        delegate.withProperties(properties)
    }

    override fun withTraceLimit(traceRateLimit: Int) = apply {
        this.traceRateLimit = traceRateLimit
    }

    override fun withTracingHeadersTypes(tracingHeadersTypes: Set<TracingHeaderType>) = apply {
        this.tracingHeadersTypes = tracingHeadersTypes
    }

    override fun withSampler(samplerAdapter: DatadogTracerSampler?): DatadogTracerBuilder = apply {
        if (samplerAdapter is DatadogTracerSamplerWrapper) delegate.sampler(samplerAdapter.delegate)
    }

    override fun withSampleRate(sampleRate: Double) = apply {
        // In case the sample rate is not set we should not specify it. The agent code under the hood
        // will provide different sampler based on this property and also different sampling priorities used
        // in the metrics
        // -1 MANUAL_DROP User indicated to drop the trace via configuration (sampling rate).
        // 0 AUTO_DROP Sampler indicated to drop the trace using a sampling rate provided by the Agent through
        // a remote configuration. The Agent API is not used in Android so this `sampling_priority:0` will never
        // be used.
        // 1 AUTO_KEEP Sampler indicated to keep the trace using a sampling rate from the default configuration
        // which right now is 100.0
        // (Default sampling priority value. or in our case no specified sample rate will be considered as 100)
        // 2 MANUAL_KEEP User indicated to keep the trace, either manually or via configuration (sampling rate)

        this.sampleRate = sampleRate
    }

    override fun withPartialFlushMinSpans(partialFlushThreshold: Int) = apply {
        delegate.partialFlushMinSpans(partialFlushThreshold)
    }

    override fun withIdGenerationStrategy(key: String, traceId128BitGenerationEnabled: Boolean) = apply {
        delegate.idGenerationStrategy(IdGenerationStrategy.fromName(key, traceId128BitGenerationEnabled))
    }

    override fun withPartialFlushThreshold(threshold: Int) = apply {
        this.partialFlushThreshold = threshold
    }

    override fun withTag(key: String, value: String) = apply {
        globalTags[key] = value
    }

    override fun setBundleWithRumEnabled(enabled: Boolean) = apply {
        bundleWithRumEnabled = enabled
    }

    private fun properties(): Properties {
        val properties = Properties()

        val propagationStyles = tracingHeadersTypes.joinToString(",")
        properties.setProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT, propagationStyles)
        properties.setProperty(TracerConfig.PROPAGATION_STYLE_INJECT, propagationStyles)
        properties.setProperty(TracerConfig.SERVICE_NAME, serviceName)
        properties.setProperty(TracerConfig.TRACE_RATE_LIMIT, traceRateLimit.toString())
        properties.setProperty(TracerConfig.PARTIAL_FLUSH_MIN_SPANS, partialFlushThreshold.toString())
        properties.setProperty(TracerConfig.URL_AS_RESOURCE_NAME, DEFAULT_URL_AS_RESOURCE_NAME.toString())
        sampleRate?.let {
            properties.setProperty(
                TracerConfig.TRACE_SAMPLE_RATE,
                (it / DEFAULT_SAMPLE_RATE).toString()
            )
        }
        properties.setProperty(
            TracerConfig.TAGS,
            globalTags.map { "${it.key}:${it.value}" }.joinToString(",")
        )

        return properties
    }

    companion object {
        internal const val MESSAGE_WRITER_NOT_PROVIDED =
            "You're trying to create an DatadogTracerBuilder instance, " +
                "but either the SDK was not initialized or the Tracing feature was " +
                "not registered. No tracing data will be sent."

        internal const val DEFAULT_SAMPLE_RATE = 100.0
        internal const val DEFAULT_PARTIAL_MIN_FLUSH = 5
        internal const val DEFAULT_URL_AS_RESOURCE_NAME = false
    }
}
