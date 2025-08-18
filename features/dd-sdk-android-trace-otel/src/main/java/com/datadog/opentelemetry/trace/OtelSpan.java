/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;

import static com.datadog.android.trace.api.DatadogTracingConstants.DEFAULT_ASYNC_PROPAGATING;
import static com.datadog.opentelemetry.trace.OtelConventions.applyNamingConvention;
import static com.datadog.opentelemetry.trace.OtelConventions.applyReservedAttribute;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.api.trace.StatusCode.OK;
import static io.opentelemetry.api.trace.StatusCode.UNSET;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.android.trace.api.DatadogTracingConstants;
import com.datadog.android.trace.api.scope.DatadogScope;
import com.datadog.android.trace.api.span.DatadogSpan;
import com.datadog.android.trace.api.span.DatadogSpanContext;
import com.datadog.android.trace.api.tracer.DatadogTracer;
import com.datadog.android.trace.internal.DatadogTracingToolkit;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

public class OtelSpan implements Span {
  private final DatadogSpan delegate;
  private StatusCode statusCode;
  private boolean recording;

  private final DatadogTracer agentTracer;

  public OtelSpan(DatadogSpan delegate, DatadogTracer agentTracer) {
    this.delegate = delegate;
    this.statusCode = UNSET;
    this.recording = true;
    this.agentTracer = agentTracer;
  }

  public static Span invalid() {
    return NoopSpan.INSTANCE;
  }

  @Override
  public <T> Span setAttribute(@NonNull AttributeKey<T> key, @Nullable T value) {
    if (this.recording && !applyReservedAttribute(this.delegate, key, value)) {
      switch (key.getType()) {
        case STRING_ARRAY:
        case BOOLEAN_ARRAY:
        case LONG_ARRAY:
        case DOUBLE_ARRAY:
          if (value instanceof List) {
            List<?> valueList = (List<?>) value;
            if (valueList.isEmpty()) {
              // Store as object to prevent delegate to remove tag when value is empty
              this.delegate.setTag(key.getKey(), (Object) "");
            } else {
              for (int index = 0; index < valueList.size(); index++) {
                this.delegate.setTag(key.getKey() + "." + index, valueList.get(index));
              }
            }
          }
          break;
        default:
          this.delegate.setTag(key.getKey(), value);
          break;
      }
    }
    return this;
  }

  @Override
  public Span addEvent(String name, Attributes attributes) {
    // Not supported
    return this;
  }

  @Override
  public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
    // Not supported
    return this;
  }

  @Override
  public Span setStatus(StatusCode statusCode, String description) {
    if (this.recording) {
      if (this.statusCode == UNSET) {
        this.statusCode = statusCode;
        this.delegate.setError(statusCode == ERROR);
        this.delegate.setErrorMessage(statusCode == ERROR ? description : null);
      } else if (this.statusCode == ERROR && statusCode == OK) {
        this.delegate.setError(false);
        this.delegate.setErrorMessage(null);
      }
    }
    return this;
  }

  public StatusCode getStatusCode() {
    return statusCode;
  }

  @Override
  public Span recordException(Throwable exception, Attributes additionalAttributes) {
    if (this.recording) {
      // Store exception as span tags as span events are not supported yet
      DatadogTracingToolkit.addThrowable(delegate, exception, DatadogTracingConstants.ErrorPriorities.UNSET);
    }
    return this;
  }

  @Override
  public Span updateName(String name) {
    if (this.recording) {
      this.delegate.setResourceName(name);
    }
    return this;
  }

  @Override
  public void end() {
    this.recording = false;
    applyNamingConvention(this.delegate);
    this.delegate.finish();
  }

  @Override
  public void end(long timestamp, TimeUnit unit) {
    this.recording = false;
    applyNamingConvention(this.delegate);
    this.delegate.finish(unit.toMicros(timestamp));
  }

  @Override
  public SpanContext getSpanContext() {
    return OtelSpanContext.fromLocalSpan(this.delegate);
  }

  @Override
  public boolean isRecording() {
    return this.recording;
  }

  public DatadogScope activate() {
    return agentTracer.activateSpan(this.delegate, DEFAULT_ASYNC_PROPAGATING);
  }

  public DatadogSpan getDatadogSpan() {
    return this.delegate;
  }

  public DatadogSpanContext getDatadogSpanContext() {
    return this.delegate.context();
  }

  static class NoopSpan implements Span {
    static final Span INSTANCE = new NoopSpan();

    @Override
    public <T> Span setAttribute(AttributeKey<T> key, T value) {
      return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes) {
      return this;
    }

    @Override
    public Span addEvent(String name, Attributes attributes, long timestamp, TimeUnit unit) {
      return this;
    }

    @Override
    public Span setStatus(StatusCode statusCode, String description) {
      return this;
    }

    @Override
    public Span recordException(Throwable exception, Attributes additionalAttributes) {
      return this;
    }

    @Override
    public Span updateName(String name) {
      return this;
    }

    @Override
    public void end() {}

    @Override
    public void end(long timestamp, TimeUnit unit) {}

    @Override
    public SpanContext getSpanContext() {
      return NoopSpanContext.INSTANCE;
    }

    @Override
    public boolean isRecording() {
      return false;
    }
  }

  private static class NoopSpanContext implements SpanContext {
    private static final SpanContext INSTANCE = new NoopSpanContext();

    @Override
    public String getTraceId() {
      return "00000000000000000000000000000000";
    }

    @Override
    public String getSpanId() {
      return "0000000000000000";
    }

    @Override
    public TraceFlags getTraceFlags() {
      return TraceFlags.getDefault();
    }

    @Override
    public TraceState getTraceState() {
      return TraceState.getDefault();
    }

    @Override
    public boolean isRemote() {
      return false;
    }
  }
}
