/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api.tracer

import com.datadog.android.trace.api.propagation.DatadogPropagation
import com.datadog.android.trace.api.propagation.NoOpDatadogPropagation
import com.datadog.android.trace.api.scope.DataScopeListener
import com.datadog.android.trace.api.scope.DatadogScope
import com.datadog.android.trace.api.span.DatadogSpan
import com.datadog.android.trace.api.span.DatadogSpanBuilder
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
interface DatadogTracer {
    fun activeSpan(): DatadogSpan?
    fun propagate(): DatadogPropagation = NoOpDatadogPropagation()
    fun activateSpan(span: DatadogSpan): DatadogScope?
    fun activateSpan(span: DatadogSpan, asyncPropagating: Boolean): DatadogScope?
    fun buildSpan(spanName: CharSequence): DatadogSpanBuilder
    fun buildSpan(instrumentationName: String, spanName: CharSequence): DatadogSpanBuilder
    fun addScopeListener(dataScopeListener: DataScopeListener)
}
