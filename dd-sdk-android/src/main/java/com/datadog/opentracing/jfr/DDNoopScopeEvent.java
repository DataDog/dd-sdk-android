/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.jfr;

/** Scope event implementation that does no reporting */
public final class DDNoopScopeEvent implements DDScopeEvent {

  public static final DDNoopScopeEvent INSTANCE = new DDNoopScopeEvent();

  @Override
  public void start() {
    // Noop
  }

  @Override
  public void finish() {
    // Noop
  }
}
