/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.android.trace.api.span.DatadogSpanContext
import com.datadog.android.trace.api.span.DatadogSpanLink
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer

internal class DatadogSpanBuilderAdapter(private val delegate: AgentTracer.SpanBuilder) : DatadogSpanBuilder {

    override fun ignoreActiveSpan() = apply { delegate.ignoreActiveSpan() }

    override fun start(): DatadogSpan = DatadogSpanAdapter(delegate.start())

    override fun withOrigin(origin: String?) = apply { delegate.withOrigin(origin) }

    override fun withStartTimestamp(micros: Long) = apply { delegate.withStartTimestamp(micros) }

    override fun withTag(key: String, value: Double?): DatadogSpanBuilder = apply { delegate.withTag(key, value) }

    override fun withTag(key: String, value: Long?): DatadogSpanBuilder = apply { delegate.withTag(key, value) }

    override fun withTag(key: String, value: Any?): DatadogSpanBuilder = apply { delegate.withTag(key, value) }

    override fun withLink(link: DatadogSpanLink): DatadogSpanBuilder = apply {
        delegate.withLink(DatadogSpanLinkAdapter(link))
    }

    override fun withResourceName(resourceName: String?): DatadogSpanBuilder = apply {
        delegate.withResourceName(resourceName)
    }

    override fun withParentContext(parentContext: DatadogSpanContext?): DatadogSpanBuilder = apply {
        if (parentContext is DatadogSpanContextAdapter) delegate.asChildOf(parentContext.delegate)
    }
}
