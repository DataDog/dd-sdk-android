/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.monitor;

public class NoOpRecording extends Recording {

  public static final Recording NO_OP = new NoOpRecording();

  @Override
  public Recording start() {
    return this;
  }

  @Override
  public void reset() {}

  @Override
  public void stop() {}

  @Override
  public void flush() {}
}
