/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.concurrent.CompletableFuture
import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.scope.DataScopeListener
import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.SpanAttributes
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource

internal class DatadogTracerAdapter(
    private val sdkCore: FeatureSdkCore,
    internal val delegate: AgentTracer.TracerAPI,
    private val bundleWithRumEnabled: Boolean
) : DatadogTracer {

    override fun buildSpan(spanName: CharSequence): DatadogSpanBuilder = DatadogSpanBuilderAdapter(
        @Suppress("DEPRECATION")
        delegate.buildSpan(spanName)
    )

    private fun DatadogSpanBuilder.withRumContext() = apply {
        if (bundleWithRumEnabled) {
            val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            if (rumFeature != null) {
                val lazyContext = CompletableFuture<DatadogContext>()
                rumFeature.withContext(withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)) {
                    lazyContext.complete(it)
                }
                withTag(SpanAttributes.DATADOG_INITIAL_CONTEXT, lazyContext)
            }
        }
    }

    override fun buildSpan(instrumentationName: String, spanName: CharSequence): DatadogSpanBuilder {
        val agentSpan = delegate.buildSpan(instrumentationName, spanName)
        return DatadogSpanBuilderAdapter(agentSpan).withRumContext()
    }

    override fun addScopeListener(dataScopeListener: DataScopeListener) {
        delegate.addScopeListener(DatadogScopeListenerAdapter(dataScopeListener))
    }

    override fun activeSpan(): DatadogSpan? = delegate.activeSpan()?.let(::DatadogSpanAdapter)

    override fun activateSpan(span: DatadogSpan): DatadogScope? = (span as? DatadogSpanAdapter)?.let {
        DatadogScopeAdapter(
            delegate.activateSpan(span.delegate, ScopeSource.INSTRUMENTATION) ?: return null
        )
    }

    override fun activateSpan(span: DatadogSpan, asyncPropagating: Boolean): DatadogScope? {
        return (span as? DatadogSpanAdapter)?.let {
            DatadogScopeAdapter(
                delegate.activateSpan(span.delegate, ScopeSource.INSTRUMENTATION, asyncPropagating) ?: return null
            )
        }
    }

    override fun propagate(): DatadogPropagation = DatadogPropagationAdapter(delegate.propagate())
}
