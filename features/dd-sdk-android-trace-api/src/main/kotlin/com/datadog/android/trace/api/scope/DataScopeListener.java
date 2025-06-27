/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.api.scope;

/** Hooks for scope activation */
public interface DataScopeListener {
  /**
   * Called just after a scope becomes the active scope
   *
   * <p>May be called multiple times. When a scope is initially created, or after a child scope is
   * deactivated.
   */
  void afterScopeActivated();

  /** Called just after a scope is closed. */
  void afterScopeClosed();
}
