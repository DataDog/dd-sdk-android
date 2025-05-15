package com.datadog.trace.api;

import com.datadog.trace.api.internal.InternalTracer;
import com.datadog.trace.api.internal.TraceSegment;

import java.util.Map;

public class EventTracker {

  public static final EventTracker NO_EVENT_TRACKER = new EventTracker(null);
  private final InternalTracer tracer;

  EventTracker(InternalTracer tracer) {
    this.tracer = tracer;
  }
}
