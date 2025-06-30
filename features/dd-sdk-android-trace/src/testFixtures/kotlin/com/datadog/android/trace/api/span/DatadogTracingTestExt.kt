/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.api.span

import com.datadog.android.trace.GlobalDatadogTracerHolder
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.impl.DatadogTracing
import com.datadog.android.trace.impl.internal.DatadogSpanContextAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanWriterWrapper
import com.datadog.android.trace.impl.internal.DatadogTracerAdapter
import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.common.writer.Writer
import com.datadog.trace.core.CoreTracer
import com.datadog.trace.core.DDSpanContext

val DatadogSpanContext.resourceName: String?
    get() = ddSpanContext?.resourceName?.toString()

val DatadogSpanContext.serviceName: String?
    get() = ddSpanContext?.serviceName?.toString()

val DatadogTracer.partialFlushMinSpans: Int?
    get() = coreTracer?.partialFlushMinSpans

fun DatadogTracing.setTracingAdapterBuilderMock(mock: DatadogTracerBuilder?) {
    builderProvider = mock
}

val DatadogTracer.writer: DatadogSpanWriter?
    get() {
        val tracerAdapter: DatadogTracerAdapter? = this as? DatadogTracerAdapter
        val coreTracer = tracerAdapter?.delegate as? CoreTracer
        val writer: Writer? = coreTracer?.getFieldValue("writer")
        return writer?.let(::DatadogSpanWriterWrapper)
    }

fun GlobalDatadogTracerHolder.clear() {
    tracer = null
}

private val DatadogSpanContext.ddSpanContext: DDSpanContext?
    get() {
        val spanContextAdapter = this as? DatadogSpanContextAdapter
        return spanContextAdapter?.delegate as? DDSpanContext
    }

private val DatadogTracer.coreTracer: CoreTracer?
    get() {
        val tracerAdapter = this as? DatadogTracerAdapter
        return tracerAdapter?.delegate as? CoreTracer
    }