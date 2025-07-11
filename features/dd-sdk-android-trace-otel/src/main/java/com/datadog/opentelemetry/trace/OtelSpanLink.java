/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.android.trace.api.span.DatadogSpanLink;
import com.datadog.android.trace.api.trace.DatadogTraceId;
import com.datadog.android.trace.internal.DatadogTracingToolkit;
import com.datadog.opentelemetry.context.propagation.TraceStateHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentelemetry.api.trace.SpanContext;

public class OtelSpanLink implements DatadogSpanLink {
  private final long spanId;
  private final boolean sampled;
  private final String traceState;
  private final DatadogTraceId traceId;
  private final Map<String, String> attributes;

  public OtelSpanLink(SpanContext spanContext) {
    this(spanContext, io.opentelemetry.api.common.Attributes.empty());
  }

  public OtelSpanLink(SpanContext spanContext, io.opentelemetry.api.common.Attributes attributes) {
    traceId = DatadogTracingToolkit.traceIdConverter.fromHex(spanContext.getTraceId());
    spanId = DatadogTracingToolkit.spanIdConverter.fromHex(spanContext.getSpanId());
    sampled = spanContext.isSampled();
    traceState = TraceStateHelper.encodeHeader(spanContext.getTraceState());
    this.attributes = convertAttributes(attributes);
  }

  private static Map<String, String> convertAttributes(io.opentelemetry.api.common.Attributes attributes) {
    if (attributes.isEmpty()) return Collections.emptyMap();


    Map<String, String> bundle = new HashMap<>();
    attributes.forEach((attributeKey, value) -> {
      switch (attributeKey.getType()) {
        case STRING:
          bundle.put(attributeKey.getKey(), (String) value);
          break;
        case BOOLEAN:
          bundle.put(attributeKey.getKey(), value.toString());
          break;
        case LONG:
          bundle.put(attributeKey.getKey(), Long.toString((long) value));
          break;
        case DOUBLE:
          bundle.put(attributeKey.getKey(), Double.toString((double) value));
          break;
        case STRING_ARRAY:
          // noinspection unchecked
          putArray(bundle, attributeKey.getKey(), (List<String>) value);
          break;
        case BOOLEAN_ARRAY:
          //noinspection unchecked,DuplicateBranchesInSwitch
          putArray(bundle, attributeKey.getKey(), (List<Boolean>) value);
          break;
        case LONG_ARRAY:
          //noinspection unchecked,DuplicateBranchesInSwitch
          putArray(bundle, attributeKey.getKey(), (List<Long>) value);
          break;
        case DOUBLE_ARRAY:
          //noinspection unchecked,DuplicateBranchesInSwitch
          putArray(bundle, attributeKey.getKey(), (List<Double>) value);
          break;
      }
    });

    return bundle;
  }

  @NonNull
  @Override
  public DatadogTraceId getTraceId() {
    return traceId;
  }

  @Override
  public long getSpanId() {
    return spanId;
  }

  @Nullable
  @Override
  public Map<String, String> getAttributes() {
    return attributes;
  }

  @Override
  public boolean getSampled() {
    return sampled;
  }

  @NonNull
  @Override
  public String getTraceStrace() {
    return traceState;
  }

  private static <T> void putArray(Map<String, String> attributes, String key, List<T> array) {
    if (key != null && array != null) {
      for (int index = 0; index < array.size(); index++) {
        Object value = array.get(index);
        if (value != null) {
          attributes.put(key + "." + index, value.toString());
        }
      }
    }
  }
}
