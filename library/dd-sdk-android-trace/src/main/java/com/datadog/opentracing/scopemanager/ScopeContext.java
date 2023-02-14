/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.scopemanager;

import io.opentracing.ScopeManager;

/** Represents a ScopeManager that is only valid in certain cases such as on a specific thread. */
@Deprecated
public interface ScopeContext extends ScopeManager {

  /**
   * When multiple ScopeContexts are active, the first one to respond true will have control.
   *
   * @return true if this ScopeContext should be active
   */
  boolean inContext();
}
