package com.datadog.trace.common.metrics;

import static java.lang.Boolean.FALSE;

import com.datadog.trace.core.CoreSpan;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class NoOpMetricsAggregator implements MetricsAggregator {

  public static final NoOpMetricsAggregator INSTANCE = new NoOpMetricsAggregator();

  @Override
  public void start() {}

  @Override
  public boolean report() {
    return false;
  }

  @Override
  public Future<Boolean> forceReport() {
    return new Future<Boolean>() {
      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
      }

      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public boolean isDone() {
        return false;
      }

      @Override
      public Boolean get() throws ExecutionException, InterruptedException {
        return FALSE;
      }

      @Override
      public Boolean get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return FALSE;
      }
    };
  }

  @Override
  public boolean publish(List<? extends CoreSpan<?>> trace) {
    return false;
  }

  @Override
  public void close() {}
}
