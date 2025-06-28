/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpanIdConverter
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.api.trace.DatadogTraceIdFactory
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.api.tracer.DatadogTracerSampler
import com.datadog.android.trace.api.tracer.NoOpDatadogTracerBuilder
import com.datadog.android.trace.impl.internal.DatadogSpanIdConverterAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanWriterWrapper
import com.datadog.android.trace.impl.internal.DatadogTraceIdFactoryAdapter
import com.datadog.android.trace.impl.internal.DatadogTracerBuilderAdapter
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

    fun newTracerBuilder(internalLogger: InternalLogger): DatadogTracerBuilder =
        DatadogTracerBuilderAdapter(internalLogger)

    fun newTracerBuilder(sdkCore: SdkCore?): DatadogTracerBuilder {
        val internalLogger = (sdkCore as? FeatureSdkCore)?.internalLogger ?: return NoOpDatadogTracerBuilder()
        return DatadogTracerBuilderAdapter(internalLogger)
    }

    fun wrapWriter(writerDelegate: Writer?): DatadogSpanWriter {
        return DatadogSpanWriterWrapper(writerDelegate ?: NoOpWriter())
    }

    fun newSampler(): DatadogTracerSampler {
        return DatadogTracerSamplerWrapper(AllSampler())
    }
}
