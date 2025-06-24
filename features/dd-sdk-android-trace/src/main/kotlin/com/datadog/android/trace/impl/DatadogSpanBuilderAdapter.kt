/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer

class DatadogSpanBuilderAdapter(private val delegate: AgentTracer.SpanBuilder) : DatadogSpanBuilder {

    override fun withOrigin(origin: String?) = apply {
        delegate.withOrigin(origin)
    }

    override fun withParentContext(parentContext: DatadogSpanContext?) = apply {
        if (parentContext !is DatadogSpanContextAdapter) return@apply
        delegate.asChildOf(parentContext.delegate)
    }

    override fun start(): DatadogSpan = DatadogSpanAdapter(delegate.start())
}
