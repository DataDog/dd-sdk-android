/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.StatusCode

fun <T : Any?> withinOtelSpan(
    operationName: String,
    otelInstrumentationName: String,
    block: () -> T
): T {
    val tracer = GlobalOpenTelemetry.getTracer(otelInstrumentationName)
    val span = tracer.spanBuilder(operationName).startSpan()
    return try {
        block()
    } catch (e: Throwable) {
        span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
        throw e
    } finally {
        span.end()
    }
}
