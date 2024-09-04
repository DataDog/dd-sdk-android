/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;

import com.datadog.android.api.InternalLogger;
import com.datadog.trace.api.DDSpanId;
import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.api.sampling.PrioritySampling;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTrace;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import com.datadog.trace.bootstrap.instrumentation.api.PathwayContext;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

import java.util.Locale;
import java.util.Map;

public class OtelExtractedContext implements AgentSpan.Context {
    private final DDTraceId traceId;
    private final long spanId;
    private final int prioritySampling;

    private OtelExtractedContext(SpanContext context) {
        this.traceId = DDTraceId.fromHex(context.getTraceId());
        this.spanId = DDSpanId.fromHex(context.getSpanId());
        this.prioritySampling =
                context.isSampled() ? PrioritySampling.SAMPLER_KEEP : PrioritySampling.UNSET;
    }

    public static AgentSpan.Context extract(Context context, InternalLogger logger) {
        Span span = Span.fromContext(context);
        SpanContext spanContext = span.getSpanContext();
        if (spanContext instanceof OtelSpanContext) {
            return ((OtelSpanContext) spanContext).delegate;
        } else if (spanContext.isValid()) {
            try {
                return new OtelExtractedContext(spanContext);
            } catch (NumberFormatException e) {
                logger.log(
                        InternalLogger.Level.DEBUG,
                        InternalLogger.Target.MAINTAINER,
                        () -> String.format(Locale.US, "Failed to convert span context with trace id = " +
                                "{%1s} and span id = {%2s}", spanContext.getSpanId(), spanContext.getTraceId()),
                        null,
                        false,
                        null);

            }
        }
        return null;
    }

    @Override
    public DDTraceId getTraceId() {
        return this.traceId;
    }

    @Override
    public long getSpanId() {
        return this.spanId;
    }

    @Override
    public AgentTrace getTrace() {
        return AgentTracer.NoopAgentTrace.INSTANCE;
    }

    @Override
    public int getSamplingPriority() {
        return this.prioritySampling;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return null;
    }

    @Override
    public PathwayContext getPathwayContext() {
        return null;
    }
}
