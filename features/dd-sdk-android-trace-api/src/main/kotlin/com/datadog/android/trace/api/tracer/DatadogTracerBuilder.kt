/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.tracer

import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.span.DatadogSpanWriter
import java.util.Properties

interface DatadogTracerBuilder {
    fun build(): DatadogTracer
    fun withProperties(properties: Properties): DatadogTracerBuilder
    fun withTracingHeadersTypes(tracingHeadersTypes: Set<TracingHeaderType>): DatadogTracerBuilder
    fun withServiceName(serviceName: String): DatadogTracerBuilder
    fun withPartialFlushMinSpans(partialFlushThreshold: Int): DatadogTracerBuilder
    fun withIdGenerationStrategy(key: String, traceId128BitGenerationEnabled: Boolean): DatadogTracerBuilder
    fun withWriter(writerAdapter: DatadogSpanWriter?): DatadogTracerBuilder
    fun withSampler(samplerAdapter: DatadogTracerSampler?): DatadogTracerBuilder
}
