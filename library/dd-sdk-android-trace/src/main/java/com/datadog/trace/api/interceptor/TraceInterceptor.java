/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.api.interceptor;

import java.util.Collection;

public interface TraceInterceptor {

  /**
   * After a trace is "complete" but before it is written, it is provided to the interceptors to
   * modify. The result following all interceptors is sampled then sent to the trace writer.
   *
   * @param trace - The collection of spans that represent a trace. Can be modified in place. Order
   *     of spans should not be relied upon.
   * @return A potentially modified or replaced collection of spans. Must not be null.
   */
  Collection<? extends MutableSpan> onTraceComplete(Collection<? extends MutableSpan> trace);

  /**
   * @return A unique priority for sorting relative to other TraceInterceptors. Unique because
   *     interceptors are stored in a sorted set, so duplicates will not be added.
   */
  int priority();
}
