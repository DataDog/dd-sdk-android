/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing;

import androidx.annotation.VisibleForTesting;

import com.datadog.android.api.InternalLogger;
import com.datadog.exec.CommonTaskExecutor;
import com.datadog.exec.CommonTaskExecutor.Task;
import com.datadog.opentracing.scopemanager.ContinuableScope;
import com.datadog.legacy.trace.common.util.Clock;

import java.io.Closeable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PendingTrace extends LinkedList<DDSpan> {
    private static final AtomicReference<SpanCleaner> SPAN_CLEANER = new AtomicReference<>();

    private final DDTracer tracer;
    private final BigInteger traceId;

    // TODO: consider moving these time fields into DDTracer to ensure that traces have precise
    // relative time
    /**
     * Trace start time in nano seconds measured up to a millisecond accuracy
     */
    private final long startTimeNano;
    /**
     * Nano second ticks value at trace start
     */
    private final long startNanoTicks;

    private final ReferenceQueue referenceQueue = new ReferenceQueue();
    private final Set<WeakReference<?>> weakReferences =
            Collections.newSetFromMap(new ConcurrentHashMap<WeakReference<?>, Boolean>());

    private final AtomicInteger pendingReferenceCount = new AtomicInteger(0);

    // We must maintain a separate count because ConcurrentLinkedDeque.size() is a linear operation.
    private final AtomicInteger completedSpanCount = new AtomicInteger(0);
    /**
     * During a trace there are cases where the root span must be accessed (e.g. priority sampling and
     * trace-search tags).
     *
     * <p>Use a weak ref because we still need to handle buggy cases where the root span is not
     * correctly closed (see SpanCleaner).
     *
     * <p>The root span will be available in non-buggy cases because it has either finished and
     * strongly ref'd in this queue or is unfinished and ref'd in a ContinuableScope.
     */
    private final AtomicReference<WeakReference<DDSpan>> rootSpan = new AtomicReference<>();

    /**
     * Ensure a trace is never written multiple times
     */
    private final AtomicBoolean isWritten = new AtomicBoolean(false);

    private final InternalLogger internalLogger;

    PendingTrace(final DDTracer tracer, final BigInteger traceId, final InternalLogger internalLogger) {
        this.tracer = tracer;
        this.traceId = traceId;
        this.internalLogger = internalLogger;

        startTimeNano = Clock.currentNanoTime();
        startNanoTicks = Clock.currentNanoTicks();

        addPendingTrace();
    }

    /**
     * Current timestamp in nanoseconds.
     *
     * <p>Note: it is not possible to get 'real' nanosecond time. This method uses trace start time
     * (which has millisecond precision) as a reference and it gets time with nanosecond precision
     * after that. This means time measured within same Trace in different Spans is relatively correct
     * with nanosecond precision.
     *
     * @return timestamp in nanoseconds
     */
    public long getCurrentTimeNano() {
        return startTimeNano + Math.max(0, Clock.currentNanoTicks() - startNanoTicks);
    }

    public void registerSpan(final DDSpan span) {
        if (traceId == null || span.context() == null) {
            internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    () -> "Span " + span.getOperationName() + " not registered because of null traceId or context; " +
                            "spanId:" + span.getSpanId() + " traceid:" + traceId,
                    null,
                    false,
                    new HashMap<>()
            );
            return;
        }
        BigInteger spanTraceId = span.context().getTraceId();
        if (!traceId.equals(spanTraceId)) {
            internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    () -> "Span " + span.getOperationName() + " not registered because of traceId mismatch; " +
                            "spanId:" + span.getSpanId() + " span.traceid:" + spanTraceId + " traceid:" + traceId,
                    null,
                    false,
                    new HashMap<>()
            );
            return;
        }
        rootSpan.compareAndSet(null, new WeakReference<>(span));
        synchronized (span) {
            if (null == span.ref) {
                span.ref = new WeakReference<DDSpan>(span, referenceQueue);
                weakReferences.add(span.ref);
                final int count = pendingReferenceCount.incrementAndGet();
            } else {
                internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        () -> "Span " + span.getOperationName() + " not registered because it is already registered; " +
                                "spanId:" + span.getSpanId() + " traceid:" + traceId,
                        null,
                        false,
                        new HashMap<>()
                );
            }
        }
    }

    private void expireSpan(final DDSpan span, final boolean write) {
        if (traceId == null || span.context() == null) {
            internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    () -> "Span " + span.getOperationName() + " not expired because of null traceId or context; " +
                            "spanId:" + span.getSpanId() + " traceid:" + traceId,
                    null,
                    false,
                    new HashMap<>()
            );
            return;
        }
        BigInteger spanTraceId = span.context().getTraceId();
        if (!traceId.equals(spanTraceId)) {
            internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    () -> "Span " + span.getOperationName() + " not expired because of traceId mismatch; " +
                            "spanId:" + span.getSpanId() + " span.traceid:" + spanTraceId + " traceid:" + traceId,
                    null,
                    false,
                    new HashMap<>()
            );
            return;
        }
        synchronized (span) {
            if (span.ref == null) {
                internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        () -> "Span " + span.getOperationName() + " not expired because it's not registered; " +
                                "spanId:" + span.getSpanId() + " traceid:" + traceId,
                        null,
                        false,
                        new HashMap<>()
                );
                return;
            }
            weakReferences.remove(span.ref);
            span.ref.clear();
            span.ref = null;
            if (write) {
                expireReference();
            } else {
                pendingReferenceCount.decrementAndGet();
            }
        }
    }

    public void dropSpan(final DDSpan span) {
        expireSpan(span, false);
        if (pendingReferenceCount.get() == 0 && weakReferences.isEmpty()) {
            // this trace is empty and doesn't have any continuations
            removePendingTrace();
        }
    }

    public void addSpan(final DDSpan span) {
        synchronized (this) {
            if (span.getDurationNano() == 0) {
                internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        () -> "Span " + span.getOperationName() + " not added because duration is zero; " +
                                "spanId:" + span.getSpanId() + " traceid:" + traceId,
                        null,
                        false,
                        new HashMap<>()
                );
                return;
            }
            if (traceId == null || span.context() == null) {
                internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        () -> "Span " + span.getOperationName() + " not added because of null traceId or context; " +
                                "spanId:" + span.getSpanId() + " traceid:" + traceId,
                        null,
                        false,
                        new HashMap<>()
                );
                return;
            }
            if (!traceId.equals(span.getTraceId())) {
                internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        () -> "Span " + span.getOperationName() + " not added because of traceId mismatch; " +
                                "spanId:" + span.getSpanId() + " traceid:" + traceId,
                        null,
                        false,
                        new HashMap<>()
                );
                return;
            }

            if (!isWritten.get()) {
                addFirst(span);
            } else {
                internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        () -> "Span " + span.getOperationName() + " not added because trace already written; " +
                                "spanId:" + span.getSpanId() + " traceid:" + traceId,
                        null,
                        false,
                        new HashMap<>()
                );
            }
            expireSpan(span, true);
        }
    }

    public DDSpan getRootSpan() {
        final WeakReference<DDSpan> rootRef = rootSpan.get();
        return rootRef == null ? null : rootRef.get();
    }

    /**
     * When using continuations, it's possible one may be used after all existing spans are otherwise
     * completed, so we need to wait till continuations are de-referenced before reporting.
     */
    public void registerContinuation(final ContinuableScope.Continuation continuation) {
        synchronized (continuation) {
            if (continuation.ref == null) {
                continuation.ref =
                        new WeakReference<ContinuableScope.Continuation>(continuation, referenceQueue);
                weakReferences.add(continuation.ref);
                final int count = pendingReferenceCount.incrementAndGet();
            } else {
            }
        }
    }

    public void cancelContinuation(final ContinuableScope.Continuation continuation) {
        synchronized (continuation) {
            if (continuation.ref == null) {
            } else {
                weakReferences.remove(continuation.ref);
                continuation.ref.clear();
                continuation.ref = null;
                expireReference();
            }
        }
    }

    private void expireReference() {
        final int count = pendingReferenceCount.decrementAndGet();
        if (count == 0) {
            write();
        } else {
            if (tracer.getPartialFlushMinSpans() > 0 && size() > tracer.getPartialFlushMinSpans()) {
                synchronized (this) {
                    if (size() > tracer.getPartialFlushMinSpans()) {
                        final DDSpan rootSpan = getRootSpan();
                        final List<DDSpan> partialTrace = new ArrayList(size());
                        final Iterator<DDSpan> it = iterator();
                        while (it.hasNext()) {
                            final DDSpan span = it.next();
                            if (span != rootSpan) {
                                partialTrace.add(span);
                                completedSpanCount.decrementAndGet();
                                it.remove();
                            }
                        }
                        tracer.write(partialTrace);
                    }
                }
            }
        }
    }

    private synchronized void write() {
        if (isWritten.compareAndSet(false, true)) {
            removePendingTrace();
            if (!isEmpty()) {
                tracer.write(this);
            }
        } else {
            internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    () -> "Trace " + traceId + " write ignored: isWritten already true",
                    null,
                    false,
                    new HashMap<>()
            );
        }
    }

    public synchronized boolean clean() {
        Reference ref;
        int count = 0;
        while ((ref = referenceQueue.poll()) != null) {
            weakReferences.remove(ref);
            if (isWritten.compareAndSet(false, true)) {
                removePendingTrace();
                // preserve throughput count.
                // Don't report the trace because the data comes from buggy uses of the api and is suspect.
                tracer.incrementTraceCount();
            }
            count++;
            expireReference();
        }
        if (count > 0) {
            // TODO attempt to flatten and report if top level spans are finished. (for accurate metrics)
        }
        return count > 0;
    }

    @Override
    public void addFirst(final DDSpan span) {
        synchronized (this) {
            super.addFirst(span);
        }
        completedSpanCount.incrementAndGet();
    }

    @Override
    public int size() {
        return completedSpanCount.get();
    }

    private void addPendingTrace() {
        final SpanCleaner cleaner = SPAN_CLEANER.get();
        if (cleaner != null) {
            cleaner.addPendingTrace(this);
        }
    }

    private void removePendingTrace() {
        final SpanCleaner cleaner = SPAN_CLEANER.get();
        if (cleaner != null) {
            cleaner.removePendingTrace(this);
        }
    }

    static void initialize() {
        final SpanCleaner oldCleaner = SPAN_CLEANER.getAndSet(new SpanCleaner());
        if (oldCleaner != null) {
            oldCleaner.close();
        }
    }

    static void close() {
        final SpanCleaner cleaner = SPAN_CLEANER.getAndSet(null);
        if (cleaner != null) {
            cleaner.close();
        }
    }

    @VisibleForTesting
    static int getPendingTracesSize() {
        final SpanCleaner cleaner = SPAN_CLEANER.get();
        if (cleaner != null) {
            return cleaner.pendingTraces.size();
        }
        return 0;
    }

    @VisibleForTesting
    static SpanCleaner getSpanCleaner() {
        return SPAN_CLEANER.get();
    }

    @VisibleForTesting
    static class SpanCleaner implements Runnable, Closeable {
        private static final long CLEAN_FREQUENCY = 1;

        private final Map<IdentityKey, PendingTrace> pendingTraces = new ConcurrentHashMap<>();

        public SpanCleaner() {
            CommonTaskExecutor.INSTANCE.scheduleAtFixedRate(
                    SpanCleanerTask.INSTANCE,
                    this,
                    0,
                    CLEAN_FREQUENCY,
                    TimeUnit.SECONDS,
                    "Pending trace cleaner");
        }

        @Override
        public void run() {
            for (final PendingTrace trace : pendingTraces.values()) {
                trace.clean();
            }
        }

        @Override
        public void close() {
            // Make sure that whatever was left over gets cleaned up
            run();
        }

        public void addPendingTrace(PendingTrace pendingTrace) {
            pendingTraces.put(new IdentityKey(pendingTrace), pendingTrace);
        }

        public void removePendingTrace(PendingTrace pendingTrace) {
            pendingTraces.remove(new IdentityKey((pendingTrace)));
        }

        @VisibleForTesting
        Map<IdentityKey, PendingTrace> getPendingTraces() {
            return pendingTraces;
        }
    }

    /**
     * This class is used to create a unique key for the pending trace map.
     * It uses the identity hash code of the object to create hash code.
     * In case of hash code collision, it compares the object references to ensure uniqueness.
     * The JVM system guarantees that `System.identityHashCode` will return same value for the lifetime of the
     * object. This will ensure that add/remove operations are legit for the same object.
     */
    @VisibleForTesting
    static class IdentityKey {
        private final PendingTrace key;

        @VisibleForTesting
        PendingTrace getKey() {
            return key;
        }

        IdentityKey(PendingTrace key) {
            this.key = key;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IdentityKey && ((IdentityKey) obj).key == key;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(key);
        }
    }

    /*
     * Important to use explicit class to avoid implicit hard references to cleaners from within executor.
     */
    private static class SpanCleanerTask implements Task<SpanCleaner> {

        static final SpanCleanerTask INSTANCE = new SpanCleanerTask();

        @Override
        public void run(final SpanCleaner target) {
            target.run();
        }
    }
}
