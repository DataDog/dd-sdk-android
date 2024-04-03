package com.datadog.trace.core.scopemanager;

import com.datadog.trace.bootstrap.instrumentation.api.AgentScope;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTrace;
import com.datadog.trace.logger.Logger;

/**
 * This class must not be a nested class of ContinuableScope to avoid an unconstrained chain of
 * references (using too much memory).
 */
abstract class AbstractContinuation implements AgentScope.Continuation {

    final ContinuableScopeManager scopeManager;
    final AgentSpan spanUnderScope;
    final byte source;
    final AgentTrace trace;

    protected final Logger logger;

    public AbstractContinuation(
            ContinuableScopeManager scopeManager,
            AgentSpan spanUnderScope,
            byte source,
            Logger logger) {
        this.scopeManager = scopeManager;
        this.spanUnderScope = spanUnderScope;
        this.source = source;
        this.trace = spanUnderScope.context().getTrace();
        this.logger = logger;
    }

    AbstractContinuation register() {
        trace.registerContinuation(this);
        return this;
    }

    // Called by ContinuableScopeManager when a continued scope is closed
    // Can't use cancel() for SingleContinuation because of the "used" check
    abstract void cancelFromContinuedScopeClose();
}
