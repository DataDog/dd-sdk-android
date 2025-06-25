/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.DatadogScope
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource

class DatadogTracerAdapter(private val delegate: AgentTracer.TracerAPI) : DatadogTracer {

    @Suppress("DEPRECATION")
    override fun buildSpan(spanName: CharSequence) = DatadogSpanBuilderAdapter(delegate.buildSpan(spanName))
    override fun activeSpan(): DatadogSpan? = delegate.activeSpan()?.let(::DatadogSpanAdapter)
    override fun activateSpan(span: DatadogSpan): DatadogScope? {
        if (span !is DatadogSpanAdapter) return null
        return DatadogScopeAdapter(delegate.activateSpan(span.delegate, ScopeSource.INSTRUMENTATION))
    }

    override fun propagate() = DatadogPropagationAdapter(delegate.propagate())
}
