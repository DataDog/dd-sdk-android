/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.android.api.InternalLogger;
import com.datadog.android.trace.api.trace.DatadogTraceId;
import com.datadog.android.trace.api.constants.DatadogTracingConstants;
import com.datadog.android.trace.api.span.DatadogSpanContext;
import com.datadog.android.trace.impl.DatadogTracing;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

public class OtelExtractedContext implements DatadogSpanContext {
    private final DatadogTraceId traceId;
    private final long spanId;
    private final int prioritySampling;

    private OtelExtractedContext(SpanContext context) {
        traceId = DatadogTracing.traceIdFactory.fromHex(context.getTraceId());
        spanId = DatadogTracing.spanIdConverter.fromHex(context.getSpanId());
        prioritySampling = context.isSampled()
                ? DatadogTracingConstants.PrioritySampling.SAMPLER_KEEP
                : DatadogTracingConstants.PrioritySampling.UNSET;
    }

    public static DatadogSpanContext extract(Context context, InternalLogger logger) {
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

    @NonNull
    @Override
    public DatadogTraceId getTraceId() {
        return traceId;
    }

    @Override
    public long getSpanId() {
        return spanId;
    }

    @Override
    public int getSamplingPriority() {
        return prioritySampling;
    }

    @Nullable
    @Override
    public Map<String, Object> getTags() {
        // Do nothing
        return Collections.emptyMap();
    }

    @Override
    public boolean setSamplingPriority(int samplingPriority) {
        // Do nothing
        return false;
    }

    @Override
    public void setMetric(@Nullable CharSequence key, double value) {
        // Do nothing
    }
}
