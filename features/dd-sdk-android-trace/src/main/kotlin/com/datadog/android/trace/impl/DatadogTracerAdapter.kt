/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl

import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer

class DatadogTracerAdapter(private val delegate: AgentTracer.TracerAPI) : DatadogTracer {
    override fun buildSpan(spanName: CharSequence) = DatadogSpanBuilderAdapter(delegate.buildSpan(spanName))
    override fun propagate() = DatadogPropagationAdapter(delegate.propagate())
}
