/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;

import androidx.annotation.NonNull;

import com.datadog.android.api.InternalLogger;
import com.datadog.android.trace.api.span.DatadogSpanBuilder;
import com.datadog.android.trace.api.tracer.DatadogTracer;
import com.datadog.opentelemetry.compat.function.Function;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

public class OtelTracer implements Tracer {

    private static final Function<SpanBuilder, SpanBuilder> NO_OP_DECORATOR = spanBuilder -> spanBuilder;

    @NonNull
    private final DatadogTracer tracer;
    @NonNull
    private final String instrumentationScopeName;
    @NonNull
    private final InternalLogger logger;

    @NonNull
    private final Function<SpanBuilder, SpanBuilder> spanBuilderDecorator;

    public OtelTracer(
            @NonNull String instrumentationScopeName,
            @NonNull DatadogTracer tracer,
            @NonNull InternalLogger logger,
            @NonNull Function<SpanBuilder, SpanBuilder> spanBuilderDecorator) {
        this.instrumentationScopeName = instrumentationScopeName;
        this.tracer = tracer;
        this.logger = logger;
        this.spanBuilderDecorator = spanBuilderDecorator;
    }

    public OtelTracer(
            @NonNull String instrumentationScopeName,
            @NonNull DatadogTracer tracer,
            @NonNull InternalLogger logger) {
        this(instrumentationScopeName, tracer, logger, NO_OP_DECORATOR);
    }

    @Override
    public SpanBuilder spanBuilder(@NonNull String spanName) {
        DatadogSpanBuilder delegate = tracer
                        .buildSpan(instrumentationScopeName, OtelConventions.SPAN_KIND_INTERNAL)
                        .withResourceName(spanName);

        return spanBuilderDecorator.apply(new OtelSpanBuilder(delegate, tracer, logger));
    }
}
