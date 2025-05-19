/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing.propagation;

import static com.datadog.opentracing.propagation.HttpCodec.validateUInt128BitsID;
import static com.datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;
import static com.datadog.trace.core.propagation.HttpCodec.RUM_SESSION_ID_KEY;
import static com.datadog.opentracing.propagation.W3CHttpCodec.BAGGAGE_KEY;
import static com.datadog.opentracing.propagation.W3CHttpCodec.RUM_SESSION_ID_BAGGAGE_KEY;

import com.datadog.android.trace.internal.domain.event.BigIntegerUtils;
import com.datadog.opentracing.DDSpanContext;
import com.datadog.legacy.trace.api.sampling.PrioritySampling;
import com.datadog.trace.api.internal.util.LongStringUtils;

import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A codec designed for HTTP transport via headers using Datadog headers
 */
class DatadogHttpCodec {

    public static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
    public static final String LEAST_SIGNIFICANT_TRACE_ID_KEY = "x-datadog-trace-id";
    public static final String MOST_SIGNIFICANT_TRACE_ID_TAG_KEY = "_dd.p.tid";
    public static final String DATADOG_TAGS_KEY = "x-datadog-tags";
    public static final String SPAN_ID_KEY = "x-datadog-parent-id";
    public static final String SAMPLING_PRIORITY_KEY = "x-datadog-sampling-priority";
    public static final String ORIGIN_KEY = "x-datadog-origin";

    private DatadogHttpCodec() {
        // This class should not be created. This also makes code coverage checks happy.
    }

    public static class Injector implements HttpCodec.Injector {

        private final BigIntegerUtils bigIntegerUtils;

        public Injector(final BigIntegerUtils bigIntegerUtils) {
            this.bigIntegerUtils = bigIntegerUtils;
        }

        public Injector() {
            this(BigIntegerUtils.INSTANCE);
        }

        @Override
        public void inject(final DDSpanContext context, final TextMapInject carrier) {
            final BigInteger traceId = context.getTraceId();
            final String leastSignificantTraceId = bigIntegerUtils.leastSignificant64BitsAsDecimal(traceId);
            final String mostSignificantTraceId = bigIntegerUtils.mostSignificant64BitsAsHex(traceId);
            final String sessionId = (String) context.getTags().get(RUM_SESSION_ID_KEY);
            carrier.put(LEAST_SIGNIFICANT_TRACE_ID_KEY, leastSignificantTraceId);
            carrier.put(SPAN_ID_KEY, context.getSpanId().toString());
            final String origin = context.getOrigin();
            if (origin != null) {
                carrier.put(ORIGIN_KEY, origin);
            }

            for (final Map.Entry<String, String> entry : context.baggageItems()) {
                carrier.put(OT_BAGGAGE_PREFIX + entry.getKey(), HttpCodec.encode(entry.getValue()));
            }
            // adding the tags

            StringBuilder tags = new StringBuilder();
            tags.append(MOST_SIGNIFICANT_TRACE_ID_TAG_KEY);
            tags.append('=');
            tags.append(mostSignificantTraceId);
            carrier.put(DATADOG_TAGS_KEY, tags.toString());

            if(sessionId != null) {
                carrier.put(BAGGAGE_KEY, RUM_SESSION_ID_BAGGAGE_KEY + '='+sessionId);
            }

            if (context.lockSamplingPriority()) {
                carrier.put(SAMPLING_PRIORITY_KEY, String.valueOf(context.getSamplingPriority()));
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
                BigInteger spanId = BigInteger.ZERO;
                int samplingPriority = PrioritySampling.UNSET;
                String origin = null;
                String mostSignificant64BitsTraceIdAsHex = null;
                String leastSignificant64BitsTraceIdAsDecimal = null;


                for (final Map.Entry<String, String> entry : carrier) {
                    final String key = entry.getKey().toLowerCase(Locale.US);
                    final String value = entry.getValue();

                    if (value == null) {
                        continue;
                    }

                    if (LEAST_SIGNIFICANT_TRACE_ID_KEY.equalsIgnoreCase(key)) {
                        leastSignificant64BitsTraceIdAsDecimal = value;
                    } else if (DATADOG_TAGS_KEY.equalsIgnoreCase(key)) {
                        mostSignificant64BitsTraceIdAsHex = extractMostSignificant64BitsTraceId(value);
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
                if (leastSignificant64BitsTraceIdAsDecimal == null || mostSignificant64BitsTraceIdAsHex == null) {
                    return new TagContext(origin, tags);
                }

                final long leastSignificantTraceId =
                        LongStringUtils.parseUnsignedLong(leastSignificant64BitsTraceIdAsDecimal);
                final String traceIdAsHex = mostSignificant64BitsTraceIdAsHex +
                        LongStringUtils.toHexStringPadded(leastSignificantTraceId, 16);
                final BigInteger traceId = validateUInt128BitsID(traceIdAsHex, 16);
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

        private String extractMostSignificant64BitsTraceId(final String tags) {
            if (tags == null) {
                return null;
            }
            final String[] tagArray = tags.split(",");
            for (String tag : tagArray) {
                final String[] tagKeyValue = tag.split("=");
                if (tagKeyValue.length >= 2 && MOST_SIGNIFICANT_TRACE_ID_TAG_KEY.equals(tagKeyValue[0])) {
                    return tagKeyValue[1];
                }
            }
            return null;
        }
    }
}
