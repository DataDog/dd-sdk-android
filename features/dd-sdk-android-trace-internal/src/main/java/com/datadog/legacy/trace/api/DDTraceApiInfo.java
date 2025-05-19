/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.legacy.trace.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DDTraceApiInfo {
  public static final String VERSION;

  static {
    String v;
    try (final BufferedReader br =
        new BufferedReader(
            new InputStreamReader(
                DDTraceApiInfo.class.getResourceAsStream("/dd-trace-api.version"), "UTF-8"))) {
      final StringBuilder sb = new StringBuilder();

      for (int c = br.read(); c != -1; c = br.read()) sb.append((char) c);

      v = sb.toString().trim();
    } catch (final Exception e) {
      v = "unknown";
    }
    VERSION = v;
  }

  public static void main(final String... args) {
    System.out.println(VERSION);
  }
}
