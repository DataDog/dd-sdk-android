/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.opentelemetry.trace;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;

@ParametersAreNonnullByDefault
public class OtelTracerBuilder implements TracerBuilder {
    @Nonnull
    private final String instrumentationScopeName;

    @Nonnull
    private final AgentTracer.TracerAPI coreTracer;

    public OtelTracerBuilder(@Nonnull String instrumentationScopeName, @Nonnull AgentTracer.TracerAPI coreTracer) {
        this.coreTracer = coreTracer;
        this.instrumentationScopeName = instrumentationScopeName;
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
        return new OtelTracer(this.instrumentationScopeName, this.coreTracer);
    }
}
