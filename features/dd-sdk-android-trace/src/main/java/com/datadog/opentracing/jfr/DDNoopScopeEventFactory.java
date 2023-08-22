/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.jfr;

import com.datadog.opentracing.DDSpanContext;

/** Event factory that returns {@link DDNoopScopeEvent} */
public final class DDNoopScopeEventFactory implements DDScopeEventFactory {
  @Override
  public DDScopeEvent create(final DDSpanContext context) {
    return DDNoopScopeEvent.INSTANCE;
  }
}
