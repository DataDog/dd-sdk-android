/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.opentelemetry.context;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AttachableWrapper;
import io.opentelemetry.context.Scope;

public class OtelScope implements Scope {
  private final Scope scope;
  private final AgentScope delegate;

  public OtelScope(Scope scope, AgentScope delegate) {
    this.scope = scope;
    this.delegate = delegate;
    if (delegate instanceof AttachableWrapper) {
      ((AttachableWrapper) delegate).attachWrapper(this);
    }
  }

  @Override
  public void close() {
    this.delegate.close();
    this.scope.close();
  }
}
