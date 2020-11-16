/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.propagation;

import static com.datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;

import com.datadog.opentracing.DDSpanContext;
import com.datadog.trace.api.sampling.PrioritySampling;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** A codec designed for HTTP transport via headers using Datadog headers */
class DatadogHttpCodec {

  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
  private static final String TRACE_ID_KEY = "x-datadog-trace-id";
  private static final String SPAN_ID_KEY = "x-datadog-parent-id";
  private static final String SAMPLING_PRIORITY_KEY = "x-datadog-sampling-priority";
  private static final String ORIGIN_KEY = "x-datadog-origin";

  private DatadogHttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMapInject carrier) {
      carrier.put(TRACE_ID_KEY, context.getTraceId().toString());
      carrier.put(SPAN_ID_KEY, context.getSpanId().toString());
      if (context.lockSamplingPriority()) {
        carrier.put(SAMPLING_PRIORITY_KEY, String.valueOf(context.getSamplingPriority()));
      }
      final String origin = context.getOrigin();
      if (origin != null) {
        carrier.put(ORIGIN_KEY, origin);
      }

      for (final Map.Entry<String, String> entry : context.baggageItems()) {
        carrier.put(OT_BAGGAGE_PREFIX + entry.getKey(), HttpCodec.encode(entry.getValue()));
      }
    }
  }

  public static class Extractor implements HttpCodec.Extractor {
    private final Map<String, String> taggedHeaders;

    public Extractor(final Map<String, String> taggedHeaders) {
      this.taggedHeaders = new HashMap<>();
      for (final Map.Entry<String, String> mapping : taggedHeaders.entrySet()) {
        this.taggedHeaders.put(mapping.getKey().trim().toLowerCase(Locale.US), mapping.getValue());
      }
    }

    @Override
    public SpanContext extract(final TextMapExtract carrier) {
      try {
        Map<String, String> baggage = Collections.emptyMap();
        Map<String, String> tags = Collections.emptyMap();
        BigInteger traceId = BigInteger.ZERO;
        BigInteger spanId = BigInteger.ZERO;
        int samplingPriority = PrioritySampling.UNSET;
        String origin = null;

        for (final Map.Entry<String, String> entry : carrier) {
          final String key = entry.getKey().toLowerCase(Locale.US);
          final String value = entry.getValue();

          if (value == null) {
            continue;
          }

          if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
            traceId = validateUInt64BitsID(value, 10);
          } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
            spanId = validateUInt64BitsID(value, 10);
          } else if (SAMPLING_PRIORITY_KEY.equalsIgnoreCase(key)) {
            samplingPriority = Integer.parseInt(value);
          } else if (ORIGIN_KEY.equalsIgnoreCase(key)) {
            origin = value;
          } else if (key.startsWith(OT_BAGGAGE_PREFIX)) {
            if (baggage.isEmpty()) {
              baggage = new HashMap<>();
            }
            baggage.put(key.replace(OT_BAGGAGE_PREFIX, ""), HttpCodec.decode(value));
          }

          if (taggedHeaders.containsKey(key)) {
            if (tags.isEmpty()) {
              tags = new HashMap<>();
            }
            tags.put(taggedHeaders.get(key), HttpCodec.decode(value));
          }
        }

        if (!BigInteger.ZERO.equals(traceId)) {
          final ExtractedContext context =
              new ExtractedContext(traceId, spanId, samplingPriority, origin, baggage, tags);
          context.lockSamplingPriority();

          return context;
        } else if (origin != null || !tags.isEmpty()) {
          return new TagContext(origin, tags);
        }
      } catch (final RuntimeException e) {
      }

      return null;
    }
  }
}
