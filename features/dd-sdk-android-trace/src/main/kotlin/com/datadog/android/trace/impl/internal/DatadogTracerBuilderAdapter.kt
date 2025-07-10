/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import androidx.annotation.VisibleForTesting
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.DatadogTracingConstants.TracerConfig
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.trace.api.IdGenerationStrategy
import com.datadog.trace.core.CoreTracer
import java.util.Properties

internal class DatadogTracerBuilderAdapter(
    private val sdkCore: FeatureSdkCore,
    private var serviceName: String,
    private val delegate: CoreTracer.CoreTracerBuilder
) : DatadogTracerBuilder {

    private var sampleRate: Double? = null
    private var bundleWithRumEnabled: Boolean = true
    private var traceRateLimit = Int.MAX_VALUE
    private var partialFlushMinSpans = DEFAULT_PARTIAL_MIN_FLUSH
    private val globalTags: MutableMap<String, String> = mutableMapOf()
    private var tracingHeadersTypes: Set<TracingHeaderType> = setOf(
        TracingHeaderType.DATADOG,
        TracingHeaderType.TRACECONTEXT
    )

    override fun build(): DatadogTracer {
        val coreTracer = delegate.withProperties(properties()).build()
        val datadogTracer = DatadogTracerAdapter(sdkCore, coreTracer, bundleWithRumEnabled)
        datadogTracer.addScopeListener(TracePropagationDataScopeListener(sdkCore, datadogTracer))

        return datadogTracer
    }

    override fun withServiceName(serviceName: String) = apply {
        this.serviceName = serviceName
    }

    override fun withTraceLimit(traceRateLimit: Int) = apply {
        this.traceRateLimit = traceRateLimit
    }

    override fun withTracingHeadersTypes(tracingHeadersTypes: Set<TracingHeaderType>) = apply {
        this.tracingHeadersTypes = tracingHeadersTypes
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

    override fun withPartialFlushMinSpans(withPartialFlushMinSpans: Int) = apply {
        this.partialFlushMinSpans = withPartialFlushMinSpans
    }

    override fun withTag(key: String, value: String) = apply {
        globalTags[key] = value
    }

    override fun setBundleWithRumEnabled(enabled: Boolean) = apply {
        bundleWithRumEnabled = enabled
    }

    internal fun setTraceId128BitGenerationEnabled(traceId128BitGenerationEnabled: Boolean) = apply {
        delegate.idGenerationStrategy(IdGenerationStrategy.fromName("SECURE_RANDOM", traceId128BitGenerationEnabled))
    }

    @VisibleForTesting
    internal fun properties(): Properties {
        val properties = Properties()

        val propagationStyles = tracingHeadersTypes.joinToString(",")
        properties.setProperty(TracerConfig.PROPAGATION_STYLE_EXTRACT, propagationStyles)
        properties.setProperty(TracerConfig.PROPAGATION_STYLE_INJECT, propagationStyles)
        properties.setProperty(TracerConfig.SERVICE_NAME, serviceName)
        properties.setProperty(TracerConfig.TRACE_RATE_LIMIT, traceRateLimit.toString())
        properties.setProperty(TracerConfig.PARTIAL_FLUSH_MIN_SPANS, partialFlushMinSpans.toString())
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
        internal const val DEFAULT_SAMPLE_RATE = 100.0
        internal const val DEFAULT_PARTIAL_MIN_FLUSH = 5
        internal const val DEFAULT_URL_AS_RESOURCE_NAME = false
    }
}
