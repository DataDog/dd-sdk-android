package com.datadog.trace.core.scopemanager;

import androidx.annotation.NonNull;

import com.datadog.trace.api.Stateful;
import com.datadog.trace.api.scopemanager.ExtendedScopeListener;
import com.datadog.trace.api.scopemanager.ScopeListener;
import com.datadog.trace.bootstrap.instrumentation.api.AgentScope;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.bootstrap.instrumentation.api.AttachableWrapper;
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.relocate.api.RatelimitedLogger;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

class ContinuableScope implements AgentScope, AttachableWrapper {
  private final ContinuableScopeManager scopeManager;

  final AgentSpan span; // package-private so scopeManager can access it directly

  /** Flag to propagate this scope across async boundaries. */
  private boolean isAsyncPropagating;

  private final byte flags;

  private short referenceCount = 1;

  private volatile Object wrapper;
  private static final AtomicReferenceFieldUpdater<ContinuableScope, Object> WRAPPER_FIELD_UPDATER =
      AtomicReferenceFieldUpdater.newUpdater(ContinuableScope.class, Object.class, "wrapper");

  private final Stateful scopeState;
  
  private final Logger log;

  private final RatelimitedLogger ratelimitedLogger;

  ContinuableScope(
          final ContinuableScopeManager scopeManager,
          final AgentSpan span,
          final byte source,
          final boolean isAsyncPropagating,
          final Stateful scopeState,
          final Logger logger,
          final RatelimitedLogger ratelimitedLogger) {
    this.scopeManager = scopeManager;
    this.span = span;
    this.flags = source;
    this.isAsyncPropagating = isAsyncPropagating;
    this.scopeState = scopeState;
    this.log = logger;
    this.ratelimitedLogger = ratelimitedLogger;
  }

  @Override
  public final void close() {
    final ScopeStack scopeStack = scopeManager.scopeStack();

    // fast check first, only perform slower check when there's an inconsistency with the stack
    if (!scopeStack.checkTop(this) && !scopeStack.checkOverdueScopes(this)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Tried to close {} scope when not on top.  Current top: {}", this, scopeStack.top);
      }

      byte source = source();
      scopeManager.healthMetrics.onScopeCloseError(source);
      if (source == ScopeSource.MANUAL.id() && scopeManager.strictMode) {
        throw new RuntimeException("Tried to close scope when not on top");
      }
    }

    final boolean alive = decrementReferences();
    scopeManager.healthMetrics.onCloseScope();
    if (!alive) {
      cleanup(scopeStack);
    }
    scopeState.close();
  }

  void cleanup(final ScopeStack scopeStack) {
    scopeStack.cleanup();
  }

  /*
   * Exists to allow stack unwinding to do a delayed call to close when the close is
   * finished properly.  e.g. When the scope is back on the top of the stack.
   *
   * DQH - If we clean-up the delegation code & notification semantics at later time,
   * I would hope this becomes unnecessary.
   */
  final void onProperClose() {
    for (final ScopeListener listener : scopeManager.scopeListeners) {
      try {
        listener.afterScopeClosed();
      } catch (Exception e) {
        log.debug("ScopeListener threw exception in close()", e);
      }
    }

    for (final ExtendedScopeListener listener : scopeManager.extendedScopeListeners) {
      try {
        listener.afterScopeClosed();
      } catch (Exception e) {
        log.debug("ScopeListener threw exception in close()", e);
      }
    }
  }

  final void incrementReferences() {
    ++referenceCount;
  }

  /** Decrements ref count -- returns true if the scope is still alive */
  final boolean decrementReferences() {
    return --referenceCount > 0;
  }

  final void clearReferences() {
    referenceCount = 0;
  }

  /** Returns true if the scope is still alive (non-zero ref count) */
  final boolean alive() {
    return referenceCount > 0;
  }

  @Override
  public final boolean isAsyncPropagating() {
    return isAsyncPropagating;
  }

  @Override
  public final AgentSpan span() {
    return span;
  }

  @Override
  public final void setAsyncPropagation(final boolean value) {
    isAsyncPropagating = value;
  }

  /**
   * The continuation returned must be closed or activated or the trace will not finish.
   *
   * @return The new continuation, or null if this scope is not async propagating.
   */
  @Override
  public final AbstractContinuation capture() {
    return isAsyncPropagating
        ? new SingleContinuation(scopeManager, span, source(), log).register()
        : null;
  }

  /**
   * The continuation returned must be closed or activated or the trace will not finish.
   *
   * @return The new continuation, or null if this scope is not async propagating.
   */
  @Override
  public final AbstractContinuation captureConcurrent() {
    return isAsyncPropagating
        ? new ConcurrentContinuation(scopeManager, span, source(), log).register()
        : null;
  }

  @Override
  public final String toString() {
    return super.toString() + "->" + span;
  }

  public final void beforeActivated() {
    try {
      scopeState.activate(span.context());
    } catch (Throwable e) {
      ratelimitedLogger.warn(
          "ScopeState {} threw exception in beforeActivated()", scopeState.getClass(), e);
    }
  }

  public final void afterActivated() {
    for (final ScopeListener listener : scopeManager.scopeListeners) {
      try {
        listener.afterScopeActivated();
      } catch (Throwable e) {
        log.debug("ScopeListener threw exception in afterActivated()", e);
      }
    }

    for (final ExtendedScopeListener listener : scopeManager.extendedScopeListeners) {
      try {
        listener.afterScopeActivated(
            span.getTraceId(),
            span.getLocalRootSpan().getSpanId(),
            span.context().getSpanId(),
            span.traceConfig());
      } catch (Throwable e) {
        log.debug(
            "ExtendedScopeListener threw exception in afterActivated()", e);
      }
    }
  }

  @Override
  public byte source() {
    return (byte) (flags & 0x7F);
  }

  @Override
  public void attachWrapper(@NonNull Object wrapper) {
    WRAPPER_FIELD_UPDATER.set(this, wrapper);
  }

  @Override
  public Object getWrapper() {
    return WRAPPER_FIELD_UPDATER.get(this);
  }
}
