/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.monitor;

public interface Monitoring {
  Monitoring DISABLED = new DisabledMonitoring();

  Recording newTimer(String name);

  Recording newTimer(String name, String... tags);

  Recording newThreadLocalTimer(String name);

  Counter newCounter(String name);

  class DisabledMonitoring implements Monitoring {
    private DisabledMonitoring() {}

    @Override
    public Recording newTimer(String name) {
      return NoOpRecording.NO_OP;
    }

    @Override
    public Recording newTimer(String name, String... tags) {
      return NoOpRecording.NO_OP;
    }

    @Override
    public Recording newThreadLocalTimer(String name) {
      return NoOpRecording.NO_OP;
    }

    @Override
    public Counter newCounter(String name) {
      return NoOpCounter.NO_OP;
    }
  }
}
