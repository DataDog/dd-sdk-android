/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.lint.InternalApi
import com.datadog.trace.api.DDTraceId
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import com.datadog.trace.bootstrap.instrumentation.api.AgentTrace
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer

/**
 * The context of a trace to be propagated through the OkHttp requests for Datadog tracing.
 */
@InternalApi
data class TraceContext(
    /**
     * The trace id.
     */
    private val traceId: DDTraceId,
    /**
     * The span id.
     */
    private val spanId: Long,
    /**
     * The sampling priority.
     */
    private val samplingPriority: Int
) : AgentSpan.Context {

    override fun getTraceId(): DDTraceId = traceId

    override fun getSpanId(): Long = spanId

    override fun getTrace(): AgentTrace = AgentTracer.NoopAgentTrace.INSTANCE

    override fun getSamplingPriority() = samplingPriority

    override fun baggageItems() = null

    override fun getPathwayContext() = null
}
