/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.trace.monitor;

public abstract class Recording implements AutoCloseable {
  @Override
  public void close() {
    stop();
  }

  public abstract Recording start();

  public abstract void reset();

  public abstract void stop();

  public abstract void flush();
}