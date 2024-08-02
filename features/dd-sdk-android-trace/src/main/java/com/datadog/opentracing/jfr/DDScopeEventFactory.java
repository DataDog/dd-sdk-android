/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.jfr;

import com.datadog.opentracing.DDSpanContext;

/** Factory that produces scope events */
public interface DDScopeEventFactory {

  /**
   * Create new scope event for given context.
   *
   * @param context span context.
   * @return scope event instance
   */
  DDScopeEvent create(final DDSpanContext context);
}
