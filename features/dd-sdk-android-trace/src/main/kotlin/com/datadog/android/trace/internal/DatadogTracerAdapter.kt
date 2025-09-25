/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.android.trace.api.scope.DatadogScopeListener
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.RumContextHelper.injectRumContextFeature
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource

internal class DatadogTracerAdapter(
    internal val sdkCore: FeatureSdkCore,
    internal val delegate: AgentTracer.TracerAPI,
    internal val bundleWithRumEnabled: Boolean,
    private val spanLogger: DatadogSpanLogger
) : DatadogTracer {

    override fun buildSpan(instrumentationName: String, spanName: CharSequence): DatadogSpanBuilder = wrapSpan(
        delegate.buildSpan(instrumentationName, spanName)
    )

    @Suppress("DEPRECATION")
    override fun buildSpan(spanName: CharSequence): DatadogSpanBuilder = wrapSpan(delegate.buildSpan(spanName))

    override fun addScopeListener(scopeListener: DatadogScopeListener) {
        delegate.addScopeListener(DatadogScopeListenerAdapter(scopeListener))
    }

    override fun propagate(): DatadogPropagation = DatadogPropagationAdapter(
        internalLogger = sdkCore.internalLogger,
        delegate = delegate.propagate()
    )

    override fun activeSpan(): DatadogSpan? = delegate.activeSpan()?.let { DatadogSpanAdapter(it, spanLogger) }

    override fun activateSpan(span: DatadogSpan): DatadogScope? = (span as? DatadogSpanAdapter)?.let {
        DatadogScopeAdapter(
            delegate.activateSpan(span.delegate, ScopeSource.INSTRUMENTATION) ?: return null
        )
    }

    internal fun activateSpan(span: DatadogSpan, asyncPropagating: Boolean): DatadogScope? {
        return (span as? DatadogSpanAdapter)
            ?.let { delegate.activateSpan(it.delegate, ScopeSource.INSTRUMENTATION, asyncPropagating) }
            ?.let { DatadogScopeAdapter(it) }
    }

    private fun wrapSpan(span: AgentTracer.SpanBuilder) =
        DatadogSpanBuilderAdapter(span, spanLogger)
            .withRumContextIfNeeded()

    private fun DatadogSpanBuilder.withRumContextIfNeeded() = apply {
        if (bundleWithRumEnabled) {
            sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.let {
                injectRumContextFeature(it)
            }
        }
    }
}
