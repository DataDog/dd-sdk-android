/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.common.exec;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class CommonTaskExecutor extends AbstractExecutorService {
  public static final CommonTaskExecutor INSTANCE = new CommonTaskExecutor();
  private static final long SHUTDOWN_WAIT_SECONDS = 5;

  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor(DaemonThreadFactory.TASK_SCHEDULER);

  private CommonTaskExecutor() {
    try {
      Runtime.getRuntime().addShutdownHook(new ShutdownCallback(executorService));
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }
  }

  public ScheduledFuture<?> scheduleAtFixedRate(
      final Runnable command, final long initialDelay, final long period, final TimeUnit unit) {
    return executorService.scheduleAtFixedRate(command, initialDelay, period, unit);
  }

  @Override
  public void shutdown() {
    executorService.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return executorService.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return executorService.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return executorService.isTerminated();
  }

  @Override
  public boolean awaitTermination(final long timeout, final TimeUnit unit)
      throws InterruptedException {
    return executorService.awaitTermination(timeout, unit);
  }

  @Override
  public void execute(final Runnable command) {
    executorService.execute(command);
  }

  private static final class ShutdownCallback extends Thread {

    private final ScheduledExecutorService executorService;

    private ShutdownCallback(final ScheduledExecutorService executorService) {
      super("dd-exec-shutdown-hook");
      this.executorService = executorService;
    }

    @Override
    public void run() {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (final InterruptedException e) {
        executorService.shutdownNow();
      }
    }
  }
}
