/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;

import androidx.annotation.VisibleForTesting;

import com.datadog.android.trace.api.span.DatadogSpan;
import com.datadog.android.trace.api.span.DatadogSpanContext;
import com.datadog.android.trace.internal.DatadogTracingToolkit;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

public class OtelSpanContext implements SpanContext {
  final DatadogSpanContext delegate;
  private final boolean sampled;
  private final boolean remote;
  private final TraceState traceState;
  private String traceId;
  private String spanId;

  public OtelSpanContext(
          DatadogSpanContext delegate, boolean sampled, boolean remote, TraceState traceState) {
    this.delegate = delegate;
    this.sampled = sampled;
    this.remote = remote;
    this.traceState = traceState;
  }

  public static SpanContext fromLocalSpan(DatadogSpan span) {
    DatadogSpanContext delegate = span.context();
    DatadogSpan localRootSpan = span.getLocalRootSpan();
    Integer samplingPriority = localRootSpan != null ? localRootSpan.getSamplingPriority() : null;
    boolean sampled = samplingPriority != null && samplingPriority > 0;
    return new OtelSpanContext(delegate, sampled, false, TraceState.getDefault());
  }

  public static SpanContext fromRemote(DatadogSpanContext extracted, TraceState traceState) {
    return new OtelSpanContext(extracted, extracted.getSamplingPriority() > 0, true, traceState);
  }

  @Override
  public String getTraceId() {
    if (this.traceId == null) {
      this.traceId = DatadogTracingToolkit.traceIdConverter.toHexString(this.delegate.getTraceId());
    }
    return this.traceId;
  }

  @Override
  public String getSpanId() {
    if (this.spanId == null) {
      this.spanId = DatadogTracingToolkit.spanIdConverter.toHexStringPadded(this.delegate.getSpanId());
    }
    return this.spanId;
  }

  @VisibleForTesting
  public DatadogSpanContext getDelegate() {
    return delegate;
  }

  @Override
  public TraceFlags getTraceFlags() {
    return this.sampled ? TraceFlags.getSampled() : TraceFlags.getDefault();
  }

  @Override
  public TraceState getTraceState() {
    return this.traceState;
  }

  @Override
  public boolean isRemote() {
    return this.remote;
  }

  @Override
  public String toString() {
    return "OtelSpanContext{"
        + "traceId='"
        + getTraceId()
        + "', spanId='"
        + getSpanId()
        + "', sampled="
        + this.sampled
        + ", remote="
        + this.remote
        + '}';
  }
}
