/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;

import static com.datadog.opentelemetry.trace.OtelConventions.ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES;
import static com.datadog.opentelemetry.trace.OtelConventions.OPERATION_NAME_SPECIFIC_ATTRIBUTE;
import static com.datadog.opentelemetry.trace.OtelConventions.toSpanKindTagValue;
import static com.datadog.opentelemetry.trace.OtelExtractedContext.extract;
import static java.lang.Boolean.parseBoolean;
import static java.util.Locale.ROOT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.android.api.InternalLogger;
import com.datadog.android.trace.api.DatadogTracingConstants;
import com.datadog.android.trace.api.span.DatadogSpan;
import com.datadog.android.trace.api.span.DatadogSpanBuilder;
import com.datadog.android.trace.api.span.DatadogSpanContext;
import com.datadog.android.trace.api.tracer.DatadogTracer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

public class OtelSpanBuilder implements SpanBuilder {
    private final DatadogSpanBuilder delegate;
    private final DatadogTracer agentTracer;

    private boolean spanKindSet;
    /**
     * Operation name overridden value by {@link OtelConventions#OPERATION_NAME_SPECIFIC_ATTRIBUTE}
     * reserved attribute ({@code null} if not set).
     */
    private String overriddenOperationName;
    /**
     * Analytics sample rate metric value from {@link
     * OtelConventions#ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES} reserved attribute ({@code -1} if not
     * set).
     */
    private int overriddenAnalyticsSampleRate;

    @NonNull
    private final InternalLogger logger;

    public OtelSpanBuilder(
            DatadogSpanBuilder delegate,
            DatadogTracer agentTracer,
            @NonNull InternalLogger logger) {
        this.delegate = delegate;
        this.spanKindSet = false;
        this.overriddenOperationName = null;
        this.overriddenAnalyticsSampleRate = -1;
        this.logger = logger;
        this.agentTracer = agentTracer;
    }

    @Override
    public SpanBuilder setParent(Context context) {
        DatadogSpanContext extractedContext = extract(context, logger);
        if (extractedContext != null) {
            this.delegate.withParentContext(extractedContext);
        }
        return this;
    }

    @Override
    public SpanBuilder setNoParent() {
        this.delegate.withParentContext(null);
        this.delegate.ignoreActiveSpan();
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext) {
        if (spanContext.isValid()) {
            this.delegate.withLink(new OtelSpanLink(spanContext));
        }
        return this;
    }

    @Override
    public SpanBuilder addLink(SpanContext spanContext, Attributes attributes) {
        if (spanContext.isValid()) {
            this.delegate.withLink(new OtelSpanLink(spanContext, attributes));
        }
        return this;
    }

    @Override
    public SpanBuilder setAttribute(@NonNull String key, @Nullable String value) {
        // Check reserved attributes
        if (OPERATION_NAME_SPECIFIC_ATTRIBUTE.equals(key) && value != null) {
            this.overriddenOperationName = value.toLowerCase(ROOT);
            return this;
        } else if (ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES.equals(key) && value != null) {
            this.overriddenAnalyticsSampleRate = parseBoolean(value) ? 1 : 0;
            return this;
        }
        // Store as object to prevent delegate to remove tag when value is empty
        this.delegate.withTag(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(@NonNull String key, long value) {
        this.delegate.withTag(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(@NonNull String key, double value) {
        this.delegate.withTag(key, value);
        return this;
    }

    @Override
    public SpanBuilder setAttribute(@NonNull String key, boolean value) {
        // Check reserved attributes
        if (ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES.equals(key)) {
            this.overriddenAnalyticsSampleRate = value ? 1 : 0;
            return this;
        }
        this.delegate.withTag(key, value);
        return this;
    }

    @Override
    public <T> SpanBuilder setAttribute(AttributeKey<T> key, @Nullable T value) {
        switch (key.getType()) {
            case STRING_ARRAY:
            case BOOLEAN_ARRAY:
            case LONG_ARRAY:
            case DOUBLE_ARRAY:
                if (value instanceof List) {
                    List<?> valueList = (List<?>) value;
                    if (valueList.isEmpty()) {
                        // Store as object to prevent delegate to remove tag when value is empty
                        this.delegate.withTag(key.getKey(), (Object) "");
                    } else {
                        for (int index = 0; index < valueList.size(); index++) {
                            this.delegate.withTag(key.getKey() + "." + index, valueList.get(index));
                        }
                    }
                }
                break;
            default:
                this.delegate.withTag(key.getKey(), value);
                break;
        }
        return this;
    }

    @Override
    public SpanBuilder setSpanKind(@Nullable SpanKind spanKind) {
        if (spanKind != null) {
            this.delegate.withTag(DatadogTracingConstants.Tags.KEY_SPAN_KIND, toSpanKindTagValue(spanKind));
            this.spanKindSet = true;
        }
        return this;
    }

    @Override
    public SpanBuilder setStartTimestamp(long startTimestamp, TimeUnit unit) {
        this.delegate.withStartTimestamp(unit.toMicros(startTimestamp));
        return this;
    }

    @Override
    public Span startSpan() {
        // Ensure the span kind is set
        if (!this.spanKindSet) {
            setSpanKind(INTERNAL);
        }
        DatadogSpan delegate = this.delegate.start();
        // Apply overrides
        if (this.overriddenOperationName != null) {
            delegate.setOperationName(this.overriddenOperationName);
        }
        if (this.overriddenAnalyticsSampleRate != -1) {
            delegate.setMetric(DatadogTracingConstants.Tags.KEY_ANALYTICS_SAMPLE_RATE, this.overriddenAnalyticsSampleRate);
        }
        return new OtelSpan(delegate, agentTracer);
    }
}
