/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.legacy.trace.api.sampling;

public class PrioritySampling {
  /**
   * Implementation detail of the client. will not be sent to the agent or propagated.
   *
   * <p>Internal value used when the priority sampling flag has not been set on the span context.
   */
  public static final int UNSET = Integer.MIN_VALUE;
  /** The sampler has decided to drop the trace. */
  public static final int SAMPLER_DROP = 0;
  /** The sampler has decided to keep the trace. */
  public static final int SAMPLER_KEEP = 1;
  /** The user has decided to drop the trace. */
  public static final int USER_DROP = -1;
  /** The user has decided to keep the trace. */
  public static final int USER_KEEP = 2;

  private PrioritySampling() {}
}
