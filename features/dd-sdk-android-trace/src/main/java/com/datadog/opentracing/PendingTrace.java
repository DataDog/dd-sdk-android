/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing;

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
  /** Trace start time in nano seconds measured up to a millisecond accuracy */
  private final long startTimeNano;
  /** Nano second ticks value at trace start */
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

  /** Ensure a trace is never written multiple times */
  private final AtomicBoolean isWritten = new AtomicBoolean(false);

  PendingTrace(final DDTracer tracer, final BigInteger traceId) {
    this.tracer = tracer;
    this.traceId = traceId;

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
      return;
    }
    if (!traceId.equals(span.context().getTraceId())) {
      return;
    }
    rootSpan.compareAndSet(null, new WeakReference<>(span));
    synchronized (span) {
      if (null == span.ref) {
        span.ref = new WeakReference<DDSpan>(span, referenceQueue);
        weakReferences.add(span.ref);
        final int count = pendingReferenceCount.incrementAndGet();
      } else {
      }
    }
  }

  private void expireSpan(final DDSpan span, final boolean write) {
    if (traceId == null || span.context() == null) {
      return;
    }
    if (!traceId.equals(span.context().getTraceId())) {
      return;
    }
    synchronized (span) {
      if (span.ref == null) {
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
  }

  public void addSpan(final DDSpan span) {
    synchronized(this) {
      if (span.getDurationNano() == 0) {
        return;
      }
      if (traceId == null || span.context() == null) {
        return;
      }
      if (!traceId.equals(span.getTraceId())) {
        return;
      }

      if (!isWritten.get()) {
        addFirst(span);
      } else {
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
      cleaner.pendingTraces.add(this);
    }
  }

  private void removePendingTrace() {
    final SpanCleaner cleaner = SPAN_CLEANER.get();
    if (cleaner != null) {
      cleaner.pendingTraces.remove(this);
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

  // FIXME: it should be possible to simplify this logic and avoid having SpanCleaner and
  // SpanCleanerTask
  private static class SpanCleaner implements Runnable, Closeable {
    private static final long CLEAN_FREQUENCY = 1;

    private final Set<PendingTrace> pendingTraces =
        Collections.newSetFromMap(new ConcurrentHashMap<PendingTrace, Boolean>());

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
      for (final PendingTrace trace : pendingTraces) {
        trace.clean();
      }
    }

    @Override
    public void close() {
      // Make sure that whatever was left over gets cleaned up
      run();
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
