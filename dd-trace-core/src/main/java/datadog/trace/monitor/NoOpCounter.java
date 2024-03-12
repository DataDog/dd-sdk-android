/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package datadog.trace.monitor;

public final class NoOpCounter implements Counter {

  public static final Counter NO_OP = new NoOpCounter();

  public void increment(int delta) {}

  public void incrementErrorCount(String cause, int delta) {}
}
