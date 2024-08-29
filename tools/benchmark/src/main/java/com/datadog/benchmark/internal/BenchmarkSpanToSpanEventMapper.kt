/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.internal

import com.datadog.benchmark.internal.model.BenchmarkContext
import com.datadog.benchmark.internal.model.SpanEvent
import io.opentelemetry.sdk.trace.data.SpanData

internal class BenchmarkSpanToSpanEventMapper {

    fun map(
        context: BenchmarkContext,
        spanData: SpanData
    ): SpanEvent {
        val durationNanos = spanData.endEpochNanos - spanData.startEpochNanos

        return SpanEvent(
            traceId = spanData.traceId.take(SPAN_EVENT_TRACE_ID_LENGTH),
            spanId = spanData.spanId,
            parentId = spanData.parentSpanId,
            resource = resolveResource(spanData),
            name = resolveOperationName(spanData),
            service = context.applicationId,
            duration = durationNanos,
            start = spanData.startEpochNanos,
            error = 0, // error is not needed in benchmark
            meta = resolveMeta(),
            metrics = resolveMetrics()
        )
    }

    private fun resolveMeta(): SpanEvent.Meta {
        // TODO: RUM-5985 Fill SpanEvent with useful meta data
        return SpanEvent.Meta(
            version = "",
            dd = SpanEvent.Dd(),
            span = SpanEvent.Span(),
            tracer = SpanEvent.Tracer(version = ""),
            usr = SpanEvent.Usr()
        )
    }

    private fun resolveOperationName(spanData: SpanData): String {
        return spanData.name ?: ""
    }

    private fun resolveResource(spanData: SpanData): String {
        return spanData.name ?: ""
    }

    private fun resolveMetrics() = SpanEvent.Metrics(
        topLevel = null,
        additionalProperties = mutableMapOf()
    )

    companion object {
        private const val SPAN_EVENT_TRACE_ID_LENGTH = 16
    }
}
