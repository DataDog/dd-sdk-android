/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.api.span

import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.impl.DatadogSpanContextAdapter
import com.datadog.android.trace.impl.DatadogSpanWriterWrapper
import com.datadog.android.trace.impl.DatadogTracerAdapter
import com.datadog.trace.core.CoreTracer
import com.datadog.trace.core.DDSpanContext
import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.common.writer.Writer

val DatadogSpanContext.resourceName: String?
    get() = ddSpanContext?.resourceName?.toString()

val DatadogSpanContext.serviceName: String?
    get() = ddSpanContext?.resourceName?.toString()

val DatadogTracer.partialFlushMinSpans: Int?
    get() = coreTracer?.partialFlushMinSpans

val DatadogTracer.writer: DatadogSpanWriter?
    get() {
        val tracerAdapter: DatadogTracerAdapter? = this as? DatadogTracerAdapter
        val coreTracer = tracerAdapter?.delegate as? CoreTracer
        val writer: Writer? = coreTracer?.getFieldValue("writer")
        return writer?.let(::DatadogSpanWriterWrapper)
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