package com.datadog.trace.core.scopemanager;

import com.datadog.trace.bootstrap.instrumentation.api.AgentScope;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.logger.Logger;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * This class must not be a nested class of ContinuableScope to avoid an unconstrained chain of
 * references (using too much memory).
 */
final class SingleContinuation extends AbstractContinuation {
  private static final AtomicIntegerFieldUpdater<SingleContinuation> USED =
      AtomicIntegerFieldUpdater.newUpdater(SingleContinuation.class, "used");
  private volatile int used = 0;

  SingleContinuation(
      final ContinuableScopeManager scopeManager,
      final AgentSpan spanUnderScope,
      final byte source,
      final Logger logger) {
    super(scopeManager, spanUnderScope, source, logger);
  }

  @Override
  public AgentScope activate() {
    if (USED.compareAndSet(this, 0, 1)) {
      return scopeManager.continueSpan(this, spanUnderScope, source);
    } else {
      logger.debug(
          "Failed to activate continuation. Reusing a continuation not allowed. Spans may be reported separately.");
      return scopeManager.continueSpan(null, spanUnderScope, source);
    }
  }

  @Override
  public void cancel() {
    if (USED.compareAndSet(this, 0, 1)) {
      trace.cancelContinuation(this);
    } else {
      logger.debug("Failed to close continuation {}. Already used.", this);
    }
  }

  @Override
  public AgentSpan getSpan() {
    return spanUnderScope;
  }

  @Override
  void cancelFromContinuedScopeClose() {
    trace.cancelContinuation(this);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "@"
        + Integer.toHexString(hashCode())
        + "->"
        + spanUnderScope;
  }
}
