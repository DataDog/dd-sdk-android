/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.opentelemetry.context.propagation;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;

public class OtelContextPropagators implements ContextPropagators {
  public static final ContextPropagators INSTANCE = new OtelContextPropagators();
  private final TextMapPropagator propagator = new AgentTextMapPropagator();

  @Override
  public TextMapPropagator getTextMapPropagator() {
    return this.propagator;
  }
}
