/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.opentelemetry.trace;


import javax.annotation.ParametersAreNonnullByDefault;

import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

@ParametersAreNonnullByDefault
public class OtelTracer implements Tracer {
//  private static final String INSTRUMENTATION_NAME = "otel";
  private final AgentTracer.TracerAPI tracer;
  private final String instrumentationScopeName;

  public OtelTracer(String instrumentationScopeName, AgentTracer.TracerAPI tracer) {
    this.instrumentationScopeName = instrumentationScopeName;
    this.tracer = tracer;
  }

  @Override
  public SpanBuilder spanBuilder(String spanName) {
    AgentTracer.SpanBuilder delegate =
        this.tracer.buildSpan(instrumentationScopeName, OtelConventions.SPAN_KIND_INTERNAL).withResourceName(spanName);
    return new OtelSpanBuilder(delegate);
  }
}
