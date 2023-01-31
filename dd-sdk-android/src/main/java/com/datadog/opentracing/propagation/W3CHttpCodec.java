/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.propagation;

import static com.datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;

import com.datadog.opentracing.DDSpanContext;
import com.datadog.trace.api.sampling.PrioritySampling;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;

/**
 * A codec designed for HTTP transport via headers using W3C traceparent header
 *
 * <p>TODO: there is fair amount of code duplication between DatadogHttpCodec and this class,
 * especially in part where TagContext is handled. We may want to refactor that and avoid special
 * handling of TagContext in other places (i.e. CompoundExtractor).
 */
class W3CHttpCodec {

  private static final String TRACEPARENT_KEY = "traceparent";
  private static final String TRACEPARENT_VALUE = "00-0000000000000000%s-%s-0%s";
  private static final String SAMPLING_PRIORITY_ACCEPT = String.valueOf(1);
  private static final String SAMPLING_PRIORITY_DROP = String.valueOf(0);
  private static final int HEX_RADIX = 16;

  private W3CHttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMapInject carrier) {
      try {
        String traceId = context.getTraceId().toString(HEX_RADIX).toLowerCase(Locale.US);
        String spanId = context.getSpanId().toString(HEX_RADIX).toLowerCase(Locale.US);

        carrier.put(TRACEPARENT_KEY, String.format(TRACEPARENT_VALUE,
                  traceId,
                  spanId,
                  convertSamplingPriority(context.getSamplingPriority())));

      } catch (final NumberFormatException e) {
      }
    }

    private String convertSamplingPriority(final int samplingPriority) {
      return samplingPriority > 0 ? SAMPLING_PRIORITY_ACCEPT : SAMPLING_PRIORITY_DROP;
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
        Map<String, String> tags = Collections.emptyMap();
        BigInteger traceId = BigInteger.ZERO;
        BigInteger spanId = BigInteger.ZERO;
        int samplingPriority = PrioritySampling.UNSET;

        for (final Map.Entry<String, String> entry : carrier) {
          final String key = entry.getKey().toLowerCase(Locale.US);
          final String value = entry.getValue();

          if (value == null) {
            continue;
          }

          if (TRACEPARENT_KEY.equalsIgnoreCase(key)) {
            String[] valueParts = value.split("-");

            if (valueParts.length != 4){
              continue;
            }

            if (valueParts[0] == "ff"){
              continue;
            }

            final int traceIdLength = valueParts[1].length();
            String trimmedValue;
            if (traceIdLength > 32) {
              traceId = BigInteger.ZERO;
              continue;
            } else if (traceIdLength > 16) {
              trimmedValue = valueParts[1].substring(traceIdLength - 16);
            } else {
              trimmedValue = valueParts[1];
            }
            traceId = validateUInt64BitsID(trimmedValue, HEX_RADIX);

            spanId = validateUInt64BitsID(valueParts[2], HEX_RADIX);

            samplingPriority = convertSamplingPriority(valueParts[3]);

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
                  new ExtractedContext(
                          traceId,
                          spanId,
                          samplingPriority,
                          null,
                          Collections.<String, String>emptyMap(),
                          tags);
          context.lockSamplingPriority();

          return context;
        } else if (!tags.isEmpty()) {
          return new TagContext(null, tags);
        }
      } catch (final RuntimeException e) {
      }

      return null;
    }

    private int convertSamplingPriority(final String samplingPriority) {
      return Integer.parseInt(samplingPriority) == 1
          ? PrioritySampling.SAMPLER_KEEP
          : PrioritySampling.SAMPLER_DROP;
    }
  }
}
