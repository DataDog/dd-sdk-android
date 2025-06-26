/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.api.InternalLogger
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.api.tracer.DatadogTracerSampler
import com.datadog.trace.api.IdGenerationStrategy
import com.datadog.trace.core.CoreTracer
import java.util.Properties

internal class DatadogTracerBuilderAdapter(internalLogger: InternalLogger) : DatadogTracerBuilder {

    private val delegate = CoreTracer.CoreTracerBuilder(internalLogger)

    override fun build(): DatadogTracer = DatadogTracerAdapter(delegate.build())

    override fun withServiceName(serviceName: String) = apply { delegate.serviceName(serviceName) }

    override fun withProperties(properties: Properties) = apply { delegate.withProperties(properties) }

    override fun withWriter(writerAdapter: DatadogSpanWriter?) = apply {
        if (writerAdapter is DatadogSpanWriterWrapper) delegate.writer(writerAdapter.delegate)
    }

    override fun withPartialFlushMinSpans(partialFlushThreshold: Int) = apply {
        delegate.partialFlushMinSpans(partialFlushThreshold)
    }

    override fun withIdGenerationStrategy(key: String, traceId128BitGenerationEnabled: Boolean) = apply {
        delegate.idGenerationStrategy(IdGenerationStrategy.fromName(key, traceId128BitGenerationEnabled))
    }

    fun sampler(sampler: DatadogTracerSampler) {
        if (sampler is DatadogTracerSamplerWrapper) delegate.sampler(sampler.delegate)
    }
}
