/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.trace.api.sampling.SamplingMechanism
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.core.DDSpan
import com.datadog.trace.core.DDSpanContext
import com.datadog.trace.core.PendingTrace

internal class DatadogSpanContextAdapter(internal val delegate: AgentSpan.Context) : DatadogSpanContext {
    override val spanId: Long get() = delegate.spanId
    override val samplingPriority: Int get() = delegate.samplingPriority
    override val tags: Map<String?, Any?>? get() = (delegate as? DDSpanContext)?.tags
    override val traceId: DatadogTraceId get() = DatadogTraceIdAdapter(delegate.traceId)

    override fun setSamplingPriority(samplingPriority: Int): Boolean {
        val delegate = delegate
        if (delegate !is DDSpanContext) return false

        return delegate.setSamplingPriority(samplingPriority, SamplingMechanism.DEFAULT.toInt())
    }

    override fun setMetric(key: CharSequence?, value: Double) {
        (delegate as? DDSpan)?.setMetric(key, value)
    }

    override fun setTracingSamplingPriorityIfNecessary() {
        (delegate.trace as? PendingTrace)?.setSamplingPriorityIfNecessary()
    }
}
