/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.api.scope.DataScopeListener
import com.datadog.android.trace.api.tracer.DatadogTracer

internal class TracePropagationDataScopeListener(
    private val sdkCore: FeatureSdkCore,
    private val datadogTracer: DatadogTracer
) : DataScopeListener {
    override fun afterScopeActivated() {
        val activeSpanContext = datadogTracer.activeSpan()?.context()
        if (activeSpanContext != null) {
            val activeSpanId = activeSpanContext.spanId.toString()
            val activeTraceId = DatadogTracingToolkit.traceIdConverter.toHexString(activeSpanContext.traceId)
            sdkCore.addActiveTraceToContext(activeTraceId, activeSpanId)
        }
    }

    override fun afterScopeClosed() {
        sdkCore.removeActiveTraceFromContext()
    }
}
