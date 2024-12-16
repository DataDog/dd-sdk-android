/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.scopemanager;

import com.datadog.opentracing.DDSpan;
import com.datadog.opentracing.jfr.DDScopeEventFactory;
import com.datadog.legacy.trace.context.ScopeListener;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.noop.NoopScopeManager;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContextualScopeManager implements ScopeManager {
  final ThreadLocal<DDScope> tlsScope = new ThreadLocal<>();
  final Deque<ScopeContext> scopeContexts = new LinkedList<>();
  final List<ScopeListener> scopeListeners = new CopyOnWriteArrayList<>();

  private final int depthLimit;
  private final DDScopeEventFactory scopeEventFactory;

  public ContextualScopeManager(final int depthLimit, final DDScopeEventFactory scopeEventFactory) {
    this.depthLimit = depthLimit;
    this.scopeEventFactory = scopeEventFactory;
  }

  @Override
  public Scope activate(final Span span, final boolean finishOnClose) {
    final Scope active = active();
    if (active instanceof DDScope) {
      final int currentDepth = ((DDScope) active).depth();
      if (depthLimit <= currentDepth) {
        return NoopScopeManager.NoopScope.INSTANCE;
      }
    }
    synchronized (scopeContexts) {
      for (final ScopeContext context : scopeContexts) {
        if (context.inContext()) {
          return context.activate(span, finishOnClose);
        }
      }
    }
    if (span instanceof DDSpan) {
      return new ContinuableScope(this, (DDSpan) span, finishOnClose, scopeEventFactory);
    } else {
      return new SimpleScope(this, span, finishOnClose);
    }
  }

  @Override
  public Scope activate(final Span span) {
    return activate(span, false);
  }

  @Override
  public Scope active() {
    synchronized (scopeContexts) {
      for (final ScopeContext csm : scopeContexts) {
        if (csm.inContext()) {
          return csm.active();
        }
      }
    }
    return tlsScope.get();
  }

  @Override
  public Span activeSpan() {
    synchronized (scopeContexts) {
      for (final ScopeContext csm : scopeContexts) {
        if (csm.inContext()) {
          return csm.activeSpan();
        }
      }
    }
    final DDScope active = tlsScope.get();
    return active == null ? null : active.span();
  }

  @Deprecated
  public void addScopeContext(final ScopeContext context) {
    synchronized (scopeContexts) {
      scopeContexts.addFirst(context);
    }
  }

  /** Attach a listener to scope activation events */
  public void addScopeListener(final ScopeListener listener) {
    scopeListeners.add(listener);
  }
}
