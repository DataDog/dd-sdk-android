/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.integration.otel

import com.datadog.tools.unit.getFieldValue
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan
import io.opentelemetry.api.trace.Span

internal fun Span.leastSignificantTraceIdAsHexString(): String {
    return spanContext.traceId.takeLast(16)
}

internal fun Span.spanIdAsHexString(): String {
    return spanContext.spanId
}

internal fun Span.expectedSpanName(): String {
    val agentSpan: AgentSpan = this.getFieldValue("delegate")
    return agentSpan.operationName.toString()
}
