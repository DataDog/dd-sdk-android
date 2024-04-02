/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;


import androidx.annotation.NonNull;

import com.datadog.android.api.InternalLogger;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer;

import java.util.function.Function;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;

public class OtelTracerBuilder implements TracerBuilder {
    @NonNull
    private final String instrumentationScopeName;

    @NonNull
    private final AgentTracer.TracerAPI coreTracer;

    @NonNull
    private final InternalLogger logger;

    @NonNull
    private final Function<SpanBuilder, SpanBuilder> spanBuilderDecorator;


    public OtelTracerBuilder(
            @NonNull String instrumentationScopeName,
            @NonNull AgentTracer.TracerAPI coreTracer,
            @NonNull InternalLogger logger,
            @NonNull Function<SpanBuilder, SpanBuilder> spanBuilderDecorator) {
        this.coreTracer = coreTracer;
        this.instrumentationScopeName = instrumentationScopeName;
        this.logger = logger;
        this.spanBuilderDecorator = spanBuilderDecorator;
    }

    @Override
    public TracerBuilder setSchemaUrl(String schemaUrl) {
        // Not supported
        return this;
    }

    @Override
    public TracerBuilder setInstrumentationVersion(String instrumentationScopeVersion) {
        // Not supported
        return this;
    }

    @Override
    public Tracer build() {
        return new OtelTracer(this.instrumentationScopeName, this.coreTracer, this.logger, this.spanBuilderDecorator);
    }
}