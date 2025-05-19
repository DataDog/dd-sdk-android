/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.exec;

import java.util.concurrent.ThreadFactory;

/** A {@link ThreadFactory} implementation that starts all {@link Thread} as daemons. */
public final class DaemonThreadFactory implements ThreadFactory {
  public static final DaemonThreadFactory TRACE_PROCESSOR =
      new DaemonThreadFactory("dd-trace-processor");
  public static final DaemonThreadFactory TRACE_WRITER = new DaemonThreadFactory("dd-trace-writer");
  public static final DaemonThreadFactory TASK_SCHEDULER =
      new DaemonThreadFactory("dd-task-scheduler");

  private final String threadName;

  /**
   * Constructs a new {@code DaemonThreadFactory} with a null ContextClassLoader.
   *
   * @param threadName used to prefix all thread names.
   */
  public DaemonThreadFactory(final String threadName) {
    this.threadName = threadName;
  }

  @Override
  public Thread newThread(final Runnable r) {
    final Thread thread = new Thread(r, threadName);
    thread.setDaemon(true);
    thread.setContextClassLoader(null);
    return thread;
  }
}
