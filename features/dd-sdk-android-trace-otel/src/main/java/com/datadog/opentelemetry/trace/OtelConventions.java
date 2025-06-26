/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentelemetry.trace;

import static com.datadog.android.trace.api.constants.DatadogTracingConstants.Tags.KEY_ANALYTICS_SAMPLE_RATE;
import static com.datadog.android.trace.api.constants.DatadogTracingConstants.Tags.VALUE_SPAN_KIND_CLIENT;
import static com.datadog.android.trace.api.constants.DatadogTracingConstants.Tags.VALUE_SPAN_KIND_CONSUMER;
import static com.datadog.android.trace.api.constants.DatadogTracingConstants.Tags.VALUE_SPAN_KIND_PRODUCER;
import static com.datadog.android.trace.api.constants.DatadogTracingConstants.Tags.VALUE_SPAN_KIND_SERVER;
import static java.lang.Boolean.parseBoolean;
import static java.util.Locale.ROOT;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.CONSUMER;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.api.trace.SpanKind.SERVER;

import androidx.annotation.Nullable;

import com.datadog.android.trace.api.constants.DatadogTracingConstants;
import com.datadog.android.trace.api.span.DatadogSpan;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;

public final class OtelConventions {
  static final String SPAN_KIND_INTERNAL = "internal";
  static final String OPERATION_NAME_SPECIFIC_ATTRIBUTE = "operation.name";
  static final String ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES = "analytics.event";
  private OtelConventions() {}

  /**
   * Convert OpenTelemetry {@link SpanKind} to {@link DatadogTracingConstants.Tags#KEY_SPAN_KIND} value.
   *
   * @param spanKind The OpenTelemetry span kind to convert.
   * @return The {@link DatadogTracingConstants.Tags#KEY_SPAN_KIND} value.
   */
  public static String toSpanKindTagValue(SpanKind spanKind) {
    switch (spanKind) {
      case CLIENT:
        return VALUE_SPAN_KIND_CLIENT;
      case SERVER:
        return VALUE_SPAN_KIND_SERVER;
      case PRODUCER:
        return VALUE_SPAN_KIND_PRODUCER;
      case CONSUMER:
        return VALUE_SPAN_KIND_CONSUMER;
      case INTERNAL:
        return SPAN_KIND_INTERNAL;
      default:
        return spanKind.toString().toLowerCase(ROOT);
    }
  }

  /**
   * Convert {@link DatadogTracingConstants.Tags#KEY_SPAN_KIND} value to OpenTelemetry {@link SpanKind}.
   *
   * @param spanKind The {@link DatadogTracingConstants.Tags#KEY_SPAN_KIND} value to convert.
   * @return The related OpenTelemetry {@link SpanKind}.
   */
  public static SpanKind toOtelSpanKind(String spanKind) {
    if (spanKind == null) {
      return INTERNAL;
    }
    switch (spanKind) {
      case VALUE_SPAN_KIND_CLIENT:
        return CLIENT;
      case VALUE_SPAN_KIND_SERVER:
        return SERVER;
      case VALUE_SPAN_KIND_PRODUCER:
        return PRODUCER;
      case VALUE_SPAN_KIND_CONSUMER:
        return CONSUMER;
      default:
        return INTERNAL;
    }
  }

  /**
   * Applies the reserved span attributes. Only OpenTelemetry specific span attributes are handled
   * here, the default ones are handled by tag interceptor while setting span attributes.
   *
   * @param span The span to apply the attributes.
   * @param key The attribute key.
   * @param value The attribute value.
   * @param <T> The attribute type.
   * @return {@code true} if the attributes is a reserved attribute applied to the span, {@code
   *     false} otherwise.
   */
  public static <T> boolean applyReservedAttribute(DatadogSpan span, AttributeKey<T> key, T value) {
    String name = key.getKey();
    switch (key.getType()) {
      case STRING:
        if (OPERATION_NAME_SPECIFIC_ATTRIBUTE.equals(name) && value instanceof String) {
          span.setOperationName(((String) value).toLowerCase(ROOT));
          return true;
        } else if (ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES.equals(name) && value instanceof String) {
          span.setMetric(KEY_ANALYTICS_SAMPLE_RATE, parseBoolean((String) value) ? 1 : 0);
          return true;
        }
      case BOOLEAN:
        if (ANALYTICS_EVENT_SPECIFIC_ATTRIBUTES.equals(name) && value instanceof Boolean) {
          span.setMetric(KEY_ANALYTICS_SAMPLE_RATE, ((Boolean) value) ? 1 : 0);
          return true;
        }
    }
    return false;
  }

  public static void applyNamingConvention(DatadogSpan span) {
    // Check if span operation name is unchanged from its default value
    if (span.getOperationName().equals(SPAN_KIND_INTERNAL)) {
      span.setOperationName(computeOperationName(span).toLowerCase(ROOT));
    }
  }

  private static String computeOperationName(DatadogSpan span) {
    Object spanKingTag = span.getTag(DatadogTracingConstants.Tags.KEY_SPAN_KIND);
    SpanKind spanKind =
        spanKingTag instanceof String ? toOtelSpanKind((String) spanKingTag) : INTERNAL;
    /*
     * HTTP convention: https://opentelemetry.io/docs/specs/otel/trace/semantic_conventions/http/
     */
    String httpRequestMethod = getStringAttribute(span, "http.request.method");
    if (spanKind == SERVER && httpRequestMethod != null) {
      return "http.server.request";
    }
    if (spanKind == CLIENT && httpRequestMethod != null) {
      return "http.client.request";
    }
    /*
     * Database convention: https://opentelemetry.io/docs/specs/semconv/database/database-spans/
     */
    String dbSystem = getStringAttribute(span, "db.system");
    if (spanKind == CLIENT && dbSystem != null) {
      return dbSystem + ".query";
    }
    /*
     * Messaging: https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/
     */
    String messagingSystem = getStringAttribute(span, "messaging.system");
    String messagingOperation = getStringAttribute(span, "messaging.operation");
    if ((spanKind == CONSUMER || spanKind == PRODUCER || spanKind == CLIENT || spanKind == SERVER)
        && messagingSystem != null
        && messagingOperation != null) {
      return messagingSystem + "." + messagingOperation;
    }
    /*
     * AWS: https://opentelemetry.io/docs/specs/semconv/cloud-providers/aws-sdk/
     */
    String rpcSystem = getStringAttribute(span, "rpc.system");
    if (spanKind == CLIENT && "aws-api".equals(rpcSystem)) {
      String service = getStringAttribute(span, "rpc.service");
      if (service == null) {
        return "aws.client.request";
      } else {
        return "aws." + service + ".request";
      }
    }
    /*
     * RPC: https://opentelemetry.io/docs/specs/semconv/rpc/rpc-spans/
     */
    if (spanKind == CLIENT && rpcSystem != null) {
      return rpcSystem + ".client.request";
    }
    if (spanKind == SERVER && rpcSystem != null) {
      return rpcSystem + ".server.request";
    }
    /*
     * FaaS:
     * https://opentelemetry.io/docs/specs/semconv/faas/faas-spans/#incoming-faas-span-attributes
     * https://opentelemetry.io/docs/specs/semconv/faas/faas-spans/#outgoing-invocations
     */
    String faasInvokedProvider = getStringAttribute(span, "faas.invoked_provider");
    String faasInvokedName = getStringAttribute(span, "faas.invoked_name");
    if (spanKind == CLIENT && faasInvokedProvider != null && faasInvokedName != null) {
      return faasInvokedProvider + "." + faasInvokedName + ".invoke";
    }
    String faasTrigger = getStringAttribute(span, "faas.trigger");
    if (spanKind == SERVER && faasTrigger != null) {
      return faasTrigger + ".invoke";
    }
    /*
     * GraphQL: https://opentelemetry.io/docs/specs/otel/trace/semantic_conventions/instrumentation/graphql/
     */
    String graphqlOperationType = getStringAttribute(span, "graphql.operation.type");
    if (graphqlOperationType != null) {
      return "graphql.server.request";
    }
    /*
     * Generic server / client: https://opentelemetry.io/docs/specs/semconv/http/http-spans/
     */
    String networkProtocolName = getStringAttribute(span, "network.protocol.name");
    if (spanKind == SERVER) {
      return networkProtocolName == null
          ? "server.request"
          : networkProtocolName + ".server.request";
    }
    if (spanKind == CLIENT) {
      return networkProtocolName == null
          ? "client.request"
          : networkProtocolName + ".client.request";
    }
    // Fallback if no convention match
    return spanKind.name();
  }

  @Nullable
  private static String getStringAttribute(DatadogSpan span, String key) {
    Object tag = span.getTag(key);
    if (tag == null) {
      return null;
    } else if (!(tag instanceof String)) {
      return key;
    }
    return (String) tag;
  }
}
