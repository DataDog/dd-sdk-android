package com.datadog.trace.core;

import static com.datadog.trace.bootstrap.instrumentation.api.SpanLinkAttributes.EMPTY;

import com.squareup.moshi.FromJson;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.ToJson;
import com.datadog.trace.api.DDSpanId;
import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpanLink;
import com.datadog.trace.bootstrap.instrumentation.api.SpanLink;
import com.datadog.trace.bootstrap.instrumentation.api.SpanLinkAttributes;
import com.datadog.trace.core.propagation.ExtractedContext;
import com.datadog.trace.core.propagation.PropagationTags;
import java.util.List;
import java.util.Map;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;

/** This class holds helper methods to encode span links into span context. */
public class DDSpanLink extends SpanLink {
  private static final Logger LOGGER = LoggerFactory.getLogger(DDSpanLink.class);
  /** The maximum of characters a span tag value can hold. */
  private static final int TAG_MAX_LENGTH = 25_000;
  /** JSON encoder (lazily initialized) */
  private static JsonAdapter<AgentSpanLink> encoder;

  protected DDSpanLink(
      DDTraceId traceId, long spanId, byte traceFlags, String traceState, Attributes attributes) {
    super(traceId, spanId, traceFlags, traceState, attributes);
  }

  /**
   * Creates a span link from an {@link ExtractedContext}. Gathers the trace and span identifiers,
   * and the W3C trace state from the given instance.
   *
   * @param context The context of the span to get the link to.
   * @return A span link to the given context.
   */
  public static SpanLink from(ExtractedContext context) {
    return from(context, EMPTY);
  }

  /**
   * Creates a span link from an {@link ExtractedContext} with custom attributes. Gathers the trace
   * and span identifiers, and the W3C trace state from the given instance.
   *
   * @param context The context of the span to get the link to.
   * @param attributes The span link attributes.
   * @return A span link to the given context with custom attributes.
   */
  public static SpanLink from(ExtractedContext context, Attributes attributes) {
    byte traceFlags = context.getSamplingPriority() > 0 ? SAMPLED_FLAG : DEFAULT_FLAGS;
    String traceState =
        context.getPropagationTags() == null
            ? ""
            : context.getPropagationTags().headerValue(PropagationTags.HeaderType.W3C);
    return new DDSpanLink(
        context.getTraceId(), context.getSpanId(), traceFlags, traceState, attributes);
  }

  /**
   * Encode a span link collection into a tag value.
   *
   * @param links The span link collection to encode.
   * @return The encoded tag value, {@code null} if no links.
   */
  public static String toTag(List<AgentSpanLink> links) {
    if (links == null || links.isEmpty()) {
      return null;
    }
    // Manually encode as JSON array
    StringBuilder builder = new StringBuilder("[");
    int index = 0;
    while (index < links.size()) {
      String linkAsJson = getEncoder().toJson(links.get(index));
      int arrayCharsNeeded = index == 0 ? 1 : 2; // Closing bracket and comma separator if needed
      if (linkAsJson.length() + builder.length() + arrayCharsNeeded >= TAG_MAX_LENGTH) {
        // Do no more fit inside a span tag, stop adding span links
        break;
      }
      if (index > 0) {
        builder.append(',');
      }
      builder.append(linkAsJson);
      index++;
    }
    // Notify of dropped links
    while (index < links.size()) {
      LOGGER.debug("Span tag full. Dropping span links {}", links.get(index));
      index++;
    }
    return builder.append(']').toString();
  }

  private static JsonAdapter<AgentSpanLink> getEncoder() {
    if (encoder == null) {
      Moshi moshi = new Moshi.Builder().add(new SpanLinkAdapter()).build();
      encoder = moshi.adapter(AgentSpanLink.class);
    }
    return encoder;
  }

  private static class SpanLinkAdapter {
    @ToJson
    SpanLinkJson toSpanLinkJson(AgentSpanLink link) {
      SpanLinkJson json = new SpanLinkJson();
      json.trace_id = link.traceId().toHexString();
      json.span_id = DDSpanId.toHexString(link.spanId());
      json.flags = link.traceFlags() == 0 ? null : link.traceFlags();
      json.tracestate = link.traceState().isEmpty() ? null : link.traceState();
      if (!link.attributes().isEmpty()) {
        json.attributes = link.attributes().asMap();
      }
      return json;
    }

    @FromJson
    AgentSpanLink fromSpanLinkJson(SpanLinkJson json) {
      return new DDSpanLink(
          DDTraceId.fromHex(json.trace_id),
          DDSpanId.fromHex(json.span_id),
          json.flags,
          json.tracestate,
          SpanLinkAttributes.fromMap(json.attributes));
    }
  }

  private static class SpanLinkJson {
    String trace_id;
    String span_id;
    Byte flags;
    String tracestate;
    Map<String, String> attributes;
  }
}