/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.scopemanager;

import io.opentracing.Scope;
import io.opentracing.Span;

// Intentionally package private.
interface DDScope extends Scope {
  @Override
  Span span();

  int depth();
}
