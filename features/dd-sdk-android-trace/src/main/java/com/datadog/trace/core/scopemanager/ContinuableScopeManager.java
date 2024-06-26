package com.datadog.trace.core.scopemanager;

import static com.datadog.trace.api.ConfigDefaults.DEFAULT_ASYNC_PROPAGATING;
import static com.datadog.trace.bootstrap.instrumentation.api.AgentTracer.NoopAgentSpan;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.datadog.android.api.InternalLogger;
import com.datadog.trace.api.Config;
import com.datadog.trace.api.Stateful;
import com.datadog.trace.api.scopemanager.ExtendedScopeListener;
import com.datadog.trace.api.scopemanager.ScopeListener;
import com.datadog.trace.bootstrap.instrumentation.api.AgentScope;
import com.datadog.trace.bootstrap.instrumentation.api.AgentScopeManager;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import com.datadog.trace.bootstrap.instrumentation.api.ProfilerContext;
import com.datadog.trace.bootstrap.instrumentation.api.ProfilingContextIntegration;
import com.datadog.trace.bootstrap.instrumentation.api.ScopeSource;
import com.datadog.trace.bootstrap.instrumentation.api.ScopeState;
import com.datadog.trace.core.monitor.HealthMetrics;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;
import com.datadog.trace.relocate.api.RatelimitedLogger;
import com.datadog.trace.util.AgentTaskScheduler;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * The primary ScopeManager. This class has ownership of the core ThreadLocal containing the
 * currently active Scope. Such scopes can be suspended with a Continuation to prevent the trace
 * from being reported even if all related spans are finished. It also delegates to other
 * ScopeInterceptors to provide additional functionality.
 */
public final class ContinuableScopeManager implements AgentScopeManager {
    private final Logger log;
    private final RatelimitedLogger ratelimitedLogger;
    static final long iterationKeepAlive =
            SECONDS.toMillis(Config.get().getScopeIterationKeepAlive());
    volatile ConcurrentMap<ScopeStack, ContinuableScope> rootIterationScopes;
    final List<ScopeListener> scopeListeners;
    final List<ExtendedScopeListener> extendedScopeListeners;
    final boolean strictMode;
    private final ScopeStackThreadLocal tlsScopeStack;
    private final int depthLimit;
    private final boolean inheritAsyncPropagation;
    final HealthMetrics healthMetrics;
    private final ProfilingContextIntegration profilingContextIntegration;

    /**
     * Constructor with NOOP Profiling and HealthMetrics implementations.
     *
     * @param depthLimit              The maximum scope depth limit, <code>0</code> for unlimited.
     * @param strictMode              Whether check if the closed spans are the active ones or not.
     * @param inheritAsyncPropagation Whether the next span should inherit the active span
     *                                asyncPropagation flag.
     */
    public ContinuableScopeManager(
            final int depthLimit, final boolean strictMode, final boolean inheritAsyncPropagation,
            final InternalLogger internalLogger) {
        this(depthLimit,
                strictMode,
                inheritAsyncPropagation,
                ProfilingContextIntegration.NoOp.INSTANCE,
                HealthMetrics.NO_OP,
                internalLogger);
    }

    /**
     * Default constructor.
     *
     * @param depthLimit              The maximum scope depth limit, <code>0</code> for unlimited.
     * @param strictMode              Whether check if the closed spans are the active ones or not.
     * @param inheritAsyncPropagation Whether the next span should inherit the active span
     *                                asyncPropagation flag.
     */
    public ContinuableScopeManager(
            final int depthLimit,
            final boolean strictMode,
            final boolean inheritAsyncPropagation,
            final ProfilingContextIntegration profilingContextIntegration,
            final HealthMetrics healthMetrics,
            final InternalLogger internalLogger) {
        this.depthLimit = depthLimit == 0 ? Integer.MAX_VALUE : depthLimit;
        this.strictMode = strictMode;
        this.inheritAsyncPropagation = inheritAsyncPropagation;
        this.scopeListeners = new CopyOnWriteArrayList<>();
        this.extendedScopeListeners = new CopyOnWriteArrayList<>();
        this.healthMetrics = healthMetrics;
        this.profilingContextIntegration = profilingContextIntegration;
        this.log = LoggerFactory.getLogger(ContinuableScopeManager.class.getSimpleName(), internalLogger);
        this.ratelimitedLogger = new RatelimitedLogger(log, 1, SECONDS);
        this.tlsScopeStack = new ScopeStackThreadLocal(profilingContextIntegration, ratelimitedLogger);
    }

    @Override
    public AgentScope activate(final AgentSpan span, final ScopeSource source) {
        return activate(span, source.id(), false, /* ignored */ false);
    }

    @Override
    public AgentScope activate(
            final AgentSpan span, final ScopeSource source, boolean isAsyncPropagating) {
        return activate(span, source.id(), true, isAsyncPropagating);
    }

    @Override
    public AgentScope.Continuation captureSpan(final AgentSpan span) {
        AbstractContinuation continuation =
                new SingleContinuation(this, span, ScopeSource.INSTRUMENTATION.id(), log);
        continuation.register();
        healthMetrics.onCaptureContinuation();
        return continuation;
    }

    private AgentScope activate(
            final AgentSpan span,
            final byte source,
            final boolean overrideAsyncPropagation,
            final boolean isAsyncPropagating) {
        ScopeStack scopeStack = scopeStack();

        final ContinuableScope top = scopeStack.top;
        if (top != null && top.span.equals(span)) {
            top.incrementReferences();
            return top;
        }

        // DQH - This check could go before the check above, since depth limit checking is fast
        final int currentDepth = scopeStack.depth();
        if (depthLimit <= currentDepth) {
            healthMetrics.onScopeStackOverflow();
            log.debug("Scope depth limit exceeded ({}).  Returning NoopScope.", currentDepth);
            return AgentTracer.NoopAgentScope.INSTANCE;
        }

        assert span != null;

        // Inherit the async propagation from the active scope unless the value is overridden
        boolean asyncPropagation =
                overrideAsyncPropagation
                        ? isAsyncPropagating
                        : inheritAsyncPropagation && top != null
                        ? top.isAsyncPropagating()
                        : DEFAULT_ASYNC_PROPAGATING;

        final ContinuableScope scope =
                new ContinuableScope(this, span, source, asyncPropagation, createScopeState(span), log, ratelimitedLogger);
        scopeStack.push(scope);
        healthMetrics.onActivateScope();

        return scope;
    }

    /**
     * Activates a scope for the given {@link AbstractContinuation}.
     *
     * @param continuation {@code null} if a continuation is re-used
     */
    ContinuableScope continueSpan(
            final AbstractContinuation continuation, final AgentSpan span, final byte source) {
        ScopeStack scopeStack = scopeStack();

        // optimization: if the top scope is already keeping the same span alive
        // then re-use that scope (avoids allocation) and cancel the continuation
        final ContinuableScope top = scopeStack.top;
        if (top != null && top.span.equals(span)) {
            top.incrementReferences();
            if (continuation != null) {
                continuation.cancelFromContinuedScopeClose();
            }
            return top;
        }

        Stateful scopeState = createScopeState(span);
        final ContinuableScope scope;
        if (continuation != null) {
            scope = new ContinuingScope(
                    this,
                    span,
                    source,
                    true,
                    continuation,
                    scopeState,
                    log,
                    ratelimitedLogger);
        } else {
            scope = new ContinuableScope(
                    this,
                    span,
                    source,
                    true,
                    scopeState,
                    log,
                    ratelimitedLogger);
        }
        scopeStack.push(scope);

        return scope;
    }

    @Override
    public void closePrevious(final boolean finishSpan) {
        ScopeStack scopeStack = scopeStack();

        // close any immediately previous iteration scope
        final ContinuableScope top = scopeStack.top;
        if (top != null && top.source() == ScopeSource.ITERATION.id()) {
            if (iterationKeepAlive > 0) { // skip depth check because cancelling is cheap
                cancelRootIterationScopeCleanup(scopeStack, top);
            }
            top.close();
            scopeStack.cleanup();
            if (finishSpan) {
                top.span.finishWithEndToEnd();
                healthMetrics.onFinishContinuation();
            }
        }
    }

    @Override
    public AgentScope activateNext(final AgentSpan span) {
        ScopeStack scopeStack = scopeStack();

        final int currentDepth = scopeStack.depth();
        if (depthLimit <= currentDepth) {
            healthMetrics.onScopeStackOverflow();
            log.debug("Scope depth limit exceeded ({}).  Returning NoopScope.", currentDepth);
            return AgentTracer.NoopAgentScope.INSTANCE;
        }

        assert span != null;

        final ContinuableScope top = scopeStack.top;

        boolean asyncPropagation =
                inheritAsyncPropagation && top != null
                        ? top.isAsyncPropagating()
                        : DEFAULT_ASYNC_PROPAGATING;

        final ContinuableScope scope =
                new ContinuableScope(
                        this,
                        span,
                        ScopeSource.ITERATION.id(),
                        asyncPropagation,
                        createScopeState(span),
                        log,
                        ratelimitedLogger);

        if (iterationKeepAlive > 0 && currentDepth == 0) {
            // no surrounding scope to aid cleanup, so use background task instead
            scheduleRootIterationScopeCleanup(scopeStack, scope);
        }

        scopeStack.push(scope);

        return scope;
    }

    @Override
    public AgentScope active() {
        return scopeStack().active();
    }

    @Override
    public AgentSpan activeSpan() {
        final ContinuableScope active = scopeStack().active();
        return active == null ? null : active.span;
    }

    /**
     * Attach a listener to scope activation events
     */
    public void addScopeListener(final ScopeListener listener) {
        if (listener instanceof ExtendedScopeListener) {
            addExtendedScopeListener((ExtendedScopeListener) listener);
        } else {
            scopeListeners.add(listener);
            log.debug("Added scope listener {}", listener);
            AgentSpan activeSpan = activeSpan();
            if (activeSpan != null) {
                // Notify the listener about the currently active scope
                listener.afterScopeActivated();
            }
        }
    }

    private void addExtendedScopeListener(final ExtendedScopeListener listener) {
        extendedScopeListeners.add(listener);
        log.debug("Added scope listener {}", listener);
        AgentSpan activeSpan = activeSpan();
        if (activeSpan != null && activeSpan != NoopAgentSpan.INSTANCE) {
            // Notify the listener about the currently active scope
            listener.afterScopeActivated(
                    activeSpan.getTraceId(),
                    activeSpan.getLocalRootSpan().getSpanId(),
                    activeSpan.context().getSpanId(),
                    activeSpan.traceConfig());
        }
    }

    private Stateful createScopeState(AgentSpan span) {
        // currently this just manages things the profiler has to do per scope, but could be expanded
        // to encapsulate other scope lifecycle activities
        // FIXME DDSpanContext is always a ProfilerContext anyway...
        if (span.context() instanceof ProfilerContext) {
            return profilingContextIntegration.newScopeState((ProfilerContext) span.context());
        }
        return Stateful.DEFAULT;
    }

    ScopeStack scopeStack() {
        return this.tlsScopeStack.get();
    }

    @Override
    public ScopeState newScopeState() {
        return new ContinuableScopeState();
    }

    private class ContinuableScopeState implements ScopeState {

        private ScopeStack localScopeStack = tlsScopeStack.initialValue();

        @Override
        public void activate() {
            tlsScopeStack.set(localScopeStack);
        }

        @Override
        public void fetchFromActive() {
            localScopeStack = tlsScopeStack.get();
        }
    }

    static final class ScopeStackThreadLocal extends ThreadLocal<ScopeStack> {

        private final ProfilingContextIntegration profilingContextIntegration;

        private final RatelimitedLogger ratelimitedLogger;

        ScopeStackThreadLocal(ProfilingContextIntegration profilingContextIntegration,
                              RatelimitedLogger ratelimitedLogger) {
            this.profilingContextIntegration = profilingContextIntegration;
            this.ratelimitedLogger = ratelimitedLogger;
        }

        @Override
        protected ScopeStack initialValue() {
            return new ScopeStack(profilingContextIntegration, ratelimitedLogger);
        }
    }

    private void scheduleRootIterationScopeCleanup(ScopeStack scopeStack, ContinuableScope scope) {
        if (rootIterationScopes == null) {
            synchronized (this) {
                if (rootIterationScopes == null) {
                    rootIterationScopes = new ConcurrentHashMap<>();
                    RootIterationCleaner.scheduleFor(rootIterationScopes);
                }
            }
        }
        rootIterationScopes.put(scopeStack, scope);
    }

    private void cancelRootIterationScopeCleanup(ScopeStack scopeStack, ContinuableScope scope) {
        if (rootIterationScopes != null) {
            rootIterationScopes.remove(scopeStack, scope);
        }
    }

    /**
     * Background task to clean-up scopes from overdue root iterations that have no surrounding scope.
     */
    private static final class RootIterationCleaner
            implements AgentTaskScheduler.Task<Map<ScopeStack, ContinuableScope>> {
        private static final RootIterationCleaner CLEANER = new RootIterationCleaner();

        public static void scheduleFor(Map<ScopeStack, ContinuableScope> rootIterationScopes) {
            long period = Math.min(iterationKeepAlive, 10_000);
            AgentTaskScheduler.INSTANCE.scheduleAtFixedRate(
                    CLEANER, rootIterationScopes, iterationKeepAlive, period, TimeUnit.MILLISECONDS);
        }

        @Override
        public void run(Map<ScopeStack, ContinuableScope> rootIterationScopes) {
            Iterator<Map.Entry<ScopeStack, ContinuableScope>> itr =
                    rootIterationScopes.entrySet().iterator();

            long cutOff = System.currentTimeMillis() - iterationKeepAlive;

            while (itr.hasNext()) {
                Map.Entry<ScopeStack, ContinuableScope> entry = itr.next();

                ScopeStack scopeStack = entry.getKey();
                ContinuableScope rootScope = entry.getValue();

                if (!rootScope.alive()) { // no need to track this anymore
                    itr.remove();
                } else if (NANOSECONDS.toMillis(rootScope.span.getStartTime()) < cutOff) {
                    // mark scope as overdue to allow cleanup and avoid further spans being attached
                    scopeStack.overdueRootScope = rootScope;
                    rootScope.span.finishWithEndToEnd();
                    itr.remove();
                }
            }
        }
    }
}
