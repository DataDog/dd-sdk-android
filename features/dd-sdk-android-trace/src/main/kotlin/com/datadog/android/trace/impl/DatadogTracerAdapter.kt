/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.span.DataScopeListener
import com.datadog.android.trace.api.span.DatadogScope
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource

internal class DatadogTracerAdapter(private val delegate: AgentTracer.TracerAPI) : DatadogTracer {

    override fun buildSpan(spanName: CharSequence): DatadogSpanBuilder = DatadogSpanBuilderAdapter(
        @Suppress("DEPRECATION")
        delegate.buildSpan(spanName)
    )

    override fun buildSpan(instrumentationName: String, spanName: CharSequence): DatadogSpanBuilder =
        DatadogSpanBuilderAdapter(
            delegate.buildSpan(instrumentationName, spanName)
        )

    override fun addScopeListener(dataScopeListener: DataScopeListener) {
        delegate.addScopeListener(DatadogScopeListenerAdapter(dataScopeListener))
    }

    override fun activeSpan(): DatadogSpan? = delegate.activeSpan()?.let(::DatadogSpanAdapter)
    override fun activateSpan(span: DatadogSpan): DatadogScope? {
        if (span !is DatadogSpanAdapter) return null
        val scope = delegate.activateSpan(span.delegate, ScopeSource.INSTRUMENTATION) ?: return null
        return DatadogScopeAdapter(scope)
    }

    override fun activateSpan(span: DatadogSpan, asyncPropagating: Boolean): DatadogScope? {
        if (span !is DatadogSpanAdapter) return null
        val scope = delegate.activateSpan(span.delegate, ScopeSource.INSTRUMENTATION, asyncPropagating) ?: return null
        return DatadogScopeAdapter(scope)
    }

    override fun propagate(): DatadogPropagation = DatadogPropagationAdapter(delegate.propagate())
}
