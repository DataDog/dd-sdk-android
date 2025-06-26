/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.api.InternalLogger
import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.api.span.DatadogSpanIdConverter
import com.datadog.android.trace.api.span.DatadogSpanWriter
import com.datadog.android.trace.api.trace.DatadogTraceIdFactory
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
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

    fun newWriter(writerDelegate: Writer?): DatadogSpanWriter {
        return DatadogSpanWriterWrapper(writerDelegate ?: NoOpWriter())
    }
}
