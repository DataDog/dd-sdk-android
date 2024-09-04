/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.propagation;

import static com.datadog.opentracing.propagation.HttpCodec.validateUInt128BitsID;
import static com.datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;

import com.datadog.opentracing.DDSpanContext;
import com.datadog.legacy.trace.api.sampling.PrioritySampling;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import kotlin.text.StringsKt;

/**
 * A codec designed for HTTP transport via headers using W3C traceparent header
 *
 * <p>TODO: there is fair amount of code duplication between DatadogHttpCodec and this class,
 * especially in part where TagContext is handled. We may want to refactor that and avoid special
 * handling of TagContext in other places (i.e. CompoundExtractor).
 */
class W3CHttpCodec {

  private static final String TRACEPARENT_KEY = "traceparent";
  private static final String TRACESTATE_KEY = "tracestate";

  private static final String TRACEPARENT_VALUE = "00-%s-%s-0%s";

  private static final int TRACECONTEXT_PARENT_ID_LENGTH = 16;
  private static final int TRACECONTEXT_TRACE_ID_LENGTH = 32;
  private static final String SAMPLING_PRIORITY_ACCEPT = String.valueOf(1);
  private static final String SAMPLING_PRIORITY_DROP = String.valueOf(0);
  private static final int HEX_RADIX = 16;

  private static final String ORIGIN_TRACESTATE_TAG_VALUE = "o";
  private static final String SAMPLING_PRIORITY_TRACESTATE_TAG_VALUE = "s";
  private static final String PARENT_SPAN_ID_TRACESTATE_TAG_VALUE = "p";
  private static final String DATADOG_VENDOR_TRACESTATE_PREFIX = "dd=";

  private W3CHttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMapInject carrier) {
      try {
        String traceId = context.getTraceId().toString(HEX_RADIX).toLowerCase(Locale.US);
        String spanId = context.getSpanId().toString(HEX_RADIX).toLowerCase(Locale.US);
        String samplingPriority = convertSamplingPriority(context.getSamplingPriority());
        String origin = context.getOrigin();

        carrier.put(TRACEPARENT_KEY, String.format(TRACEPARENT_VALUE,
                  StringsKt.padStart(traceId, TRACECONTEXT_TRACE_ID_LENGTH, '0'),
                  StringsKt.padStart(spanId, TRACECONTEXT_PARENT_ID_LENGTH, '0'),
                  samplingPriority));
        // TODO RUM-2121 3rd party vendor information will be erased
        carrier.put(TRACESTATE_KEY, createTraceStateHeader(samplingPriority, origin, spanId));

      } catch (final NumberFormatException e) {
      }
    }

    private String convertSamplingPriority(final int samplingPriority) {
      return samplingPriority > 0 ? SAMPLING_PRIORITY_ACCEPT : SAMPLING_PRIORITY_DROP;
    }

    private String createTraceStateHeader(
            String samplingPriority,
            String origin,
            String parentSpanId
    ) {
      StringBuilder sb = new StringBuilder(DATADOG_VENDOR_TRACESTATE_PREFIX)
              .append(SAMPLING_PRIORITY_TRACESTATE_TAG_VALUE)
              .append(":")
              .append(samplingPriority)
              .append(";")
              .append(PARENT_SPAN_ID_TRACESTATE_TAG_VALUE)
              .append(":")
              .append(parentSpanId);

      if (origin != null) {
        sb.append(";")
                .append(ORIGIN_TRACESTATE_TAG_VALUE)
                .append(":")
                .append(origin.toLowerCase(Locale.US));
      }

      return sb.toString();
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
        String origin = null;

        for (final Map.Entry<String, String> entry : carrier) {
          final String key = entry.getKey().toLowerCase(Locale.US);
          final String value = entry.getValue();

          if (value == null) {
            continue;
          }

          if (TRACEPARENT_KEY.equalsIgnoreCase(key)) {
            // version - traceId - parentId - traceFlags
            String[] valueParts = value.split("-");

            if (valueParts.length != 4){
              continue;
            }

            if ("ff".equalsIgnoreCase(valueParts[0])){
              // ff version is forbidden
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
            traceId = validateUInt128BitsID(trimmedValue, HEX_RADIX);

            spanId = validateUInt64BitsID(valueParts[2], HEX_RADIX);

            samplingPriority = convertSamplingPriority(valueParts[3]);

          } else if (TRACESTATE_KEY.equalsIgnoreCase(key)) {
            Map<String, String> datadogTraceStateTags = extractDatadogTagsFromTraceState(value);
            origin = datadogTraceStateTags.get(ORIGIN_TRACESTATE_TAG_VALUE);
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
                          origin,
                          Collections.<String, String>emptyMap(),
                          tags);
          context.lockSamplingPriority();

          return context;
        } else if (!tags.isEmpty()) {
          return new TagContext(origin, tags);
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

    private Map<String, String> extractDatadogTagsFromTraceState(String traceState) {
      String[] vendors = traceState.split(",");
      Map<String, String> tags = new HashMap<>();
      for (String vendor : vendors) {
        if (vendor.startsWith(DATADOG_VENDOR_TRACESTATE_PREFIX)) {
          String[] vendorTags = vendor.substring(DATADOG_VENDOR_TRACESTATE_PREFIX.length())
                  .split(";");
          for (String vendorTag : vendorTags) {
            String[] keyAndValue = vendorTag.split(":");
            if (keyAndValue.length == 2) {
              tags.put(keyAndValue[0], keyAndValue[1]);
            }
          }
        }
      }
      return tags;
    }
  }
}
