package com.datadog.trace.api;

/** A class with Datadog tracer features. */
public interface Tracer {

  /** Get the trace id of the active trace. Returns 0 if there is no active trace. */
  String getTraceId();

  /**
   * Get the span id of the active span of the active trace. Returns 0 if there is no active trace.
   */
  String getSpanId();
}
