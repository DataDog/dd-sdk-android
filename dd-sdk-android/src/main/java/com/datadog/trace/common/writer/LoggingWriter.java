/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.common.writer;

import com.datadog.opentracing.DDSpan;
import java.util.List;

public class LoggingWriter implements Writer {

  @Override
  public void write(final List<DDSpan> trace) {
  }

  @Override
  public void incrementTraceCount() {
  }

  @Override
  public void close() {
  }

  @Override
  public void start() {
  }

  @Override
  public String toString() {
    return "LoggingWriter { }";
  }
}
