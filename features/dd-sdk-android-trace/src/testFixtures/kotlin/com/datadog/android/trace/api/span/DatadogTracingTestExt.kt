/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.api.span

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.GlobalDatadogTracerHolder
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.DatadogTracerBuilder
import com.datadog.android.trace.impl.DatadogTracing
import com.datadog.android.trace.impl.internal.DatadogSpanAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanContextAdapter
import com.datadog.android.trace.impl.internal.DatadogSpanLoggerAdapter
import com.datadog.android.trace.impl.internal.DatadogTracerAdapter
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.trace.core.CoreTracer
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import com.google.gson.JsonElement

val DatadogSpanContext.resourceName: String?
    get() = ddSpanContext?.resourceName?.toString()

val DatadogSpanContext.serviceName: String?
    get() = ddSpanContext?.serviceName?.toString()

val DatadogTracer.partialFlushMinSpans: Int?
    get() = coreTracer?.partialFlushMinSpans

fun DatadogTracing.setTracingAdapterBuilderMock(mock: DatadogTracerBuilder?) {
    builderProvider = mock
}

fun DatadogTracing.setSpanLoggerMock(sdkCore: FeatureSdkCore?) {
    spanLoggerProvider = sdkCore?.let(::DatadogSpanLoggerAdapter)
}

fun DatadogTracing.clear() {
    setSpanLoggerMock(null)
    setTracingAdapterBuilderMock(null)
}

fun DatadogSpan.resolveMeta(datadogContext: DatadogContext): JsonElement {
    val mapper = CoreTracerSpanToSpanEventMapper(false)
    val ddSpan = (this as DatadogSpanAdapter).delegate as DDSpan
    return mapper.resolveMeta(datadogContext, ddSpan).toJson()
}

fun DatadogSpan.resolveMetrics(): JsonElement {
    val mapper = CoreTracerSpanToSpanEventMapper(false)
    val ddSpan = (this as DatadogSpanAdapter).delegate as DDSpan
    return mapper.resolveMetrics(ddSpan).toJson()
}

fun DatadogSpan.forceSamplingDecision() {
    (this as DatadogSpanAdapter).delegate.forceSamplingDecision()
}

fun GlobalDatadogTracerHolder.clear() {
    instance = null
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
