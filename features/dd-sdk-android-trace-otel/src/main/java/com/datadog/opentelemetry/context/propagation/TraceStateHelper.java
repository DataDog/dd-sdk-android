/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.context.propagation;

import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.TraceStateBuilder;

/** This class helps to decode W3C tracestate header into a {@link TraceState} instance. */
public final class TraceStateHelper {
  private static final int TRACESTATE_MAX_SIZE = 512;
  private static final int TRACESTATE_MAX_MEMBERS = 32;
  private static final char TRACESTATE_ENTRY_DELIMITER = ',';
  private static final char TRACESTATE_KEY_VALUE_DELIMITER = '=';

  private TraceStateHelper() {}

  // Inspired from W3CTraceContextEncoding.decodeTraceState only available in API later versions.
  // Uses literal comma scanning instead of a regex split to avoid quadratic backtracking on long
  // whitespace runs (CVE-2026-118259).
  public static TraceState decodeHeader(String header) {
    TraceStateBuilder traceStateBuilder = TraceState.builder();
    int memberCount = 0;
    int end = header.length();
    // Scan backwards because the builder prepends entries.
    while (end > 0) {
      int start = header.lastIndexOf(TRACESTATE_ENTRY_DELIMITER, end - 1) + 1;
      int nextEnd = start - 1;
      while (start < end && isOptionalWhitespace(header.charAt(start))) {
        start++;
      }
      while (end > start && isOptionalWhitespace(header.charAt(end - 1))) {
        end--;
      }
      if (start < end) {
        memberCount++;
        if (memberCount > TRACESTATE_MAX_MEMBERS) {
          return TraceState.getDefault();
        }
        int index = header.indexOf(TRACESTATE_KEY_VALUE_DELIMITER, start);
        if (index == -1 || index >= end) {
          return TraceState.getDefault();
        }
        traceStateBuilder.put(header.substring(start, index), header.substring(index + 1, end));
      }
      end = nextEnd;
    }

    TraceState traceState = traceStateBuilder.build();
    if (traceState.size() != memberCount) {
      return TraceState.getDefault();
    }
    return traceState;
  }

  // Inspired from W3CTraceContextEncoding.encodeTraceState only available in API later versions.
  public static String encodeHeader(TraceState traceState) {
    if (traceState.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder(TRACESTATE_MAX_SIZE);
    traceState.forEach(
        (key, value) -> {
          if (builder.length() != 0) {
            builder.append(TRACESTATE_ENTRY_DELIMITER);
          }
          builder.append(key).append(TRACESTATE_KEY_VALUE_DELIMITER).append(value);
        });
    return builder.toString();
  }

  private static boolean isOptionalWhitespace(char character) {
    return character == ' ' || character == '\t';
  }
}
