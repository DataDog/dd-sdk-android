package com.datadog.trace.core;

import static com.datadog.trace.bootstrap.instrumentation.api.SpanLinkAttributes.EMPTY;

import com.datadog.trace.api.DDSpanId;
import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpanLink;
import com.datadog.trace.bootstrap.instrumentation.api.SpanLink;
import com.datadog.trace.core.propagation.ExtractedContext;
import com.datadog.trace.core.propagation.PropagationTags;
import com.datadog.trace.core.util.MapUtils;
import com.datadog.trace.logger.Logger;
import com.datadog.trace.logger.LoggerFactory;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * This class holds helper methods to encode span links into span context.
 */
public class DDSpanLink extends SpanLink {
    private static final Logger LOGGER = LoggerFactory.getLogger(DDSpanLink.class);
    /**
     * The maximum of characters a span tag value can hold.
     */
    private static final int TAG_MAX_LENGTH = 25_000;

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
     * @param context    The context of the span to get the link to.
     * @param attributes The span link attributes.
     * @return A span link to the given context with custom attributes.
     */
    public static SpanLink from(ExtractedContext context, Attributes attributes) {
        byte traceFlags = context.getTraceSamplingPriority() > 0 ? SAMPLED_FLAG : DEFAULT_FLAGS;
        String traceState =
                context.getPropagationTags() == null
                        ? ""
                        : context.getPropagationTags().headerValue(PropagationTags.HeaderType.W3C);
        return new DDSpanLink(
                context.getTraceId(), context.getSpanId(), traceFlags, traceState, attributes);
    }

    // This method is only called from the ListWriter class which is used only in the unit tests
    // To keep the unit tests working, we need to keep this method but the implementation is not really
    // needed in this context. SpanLinks serialization is handled in our OtelDDSpanToSpanEventMapper and
    // tests properly there so there is no need to look into this method.

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
            String serializedLink = asJsonString(links.get(index));
            int arrayCharsNeeded = index == 0 ? 1 : 2; // Closing bracket and comma separator if needed
            if (serializedLink.length() + builder.length() + arrayCharsNeeded >= TAG_MAX_LENGTH) {
                // Do no more fit inside a span tag, stop adding span links
                break;
            }
            if (index > 0) {
                builder.append(',');
            }
            builder.append(serializedLink);
            index++;
        }
        // Notify of dropped links
        while (index < links.size()) {
            LOGGER.debug("Span tag full. Dropping span links {}", links.get(index));
            index++;
        }
        return builder.append(']').toString();
    }

    private static String asJsonString(AgentSpanLink link) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("trace_id", link.traceId().toHexString());
        jsonObject.addProperty("span_id", DDSpanId.toHexString(link.spanId()));
        jsonObject.addProperty("flags", link.traceFlags() == 0 ? null : link.traceFlags());
        jsonObject.addProperty("tracestate", link.traceState().isEmpty() ? null : link.traceState());
        if (!link.attributes().isEmpty()) {
            String mapAsJson = MapUtils.getAsJsonObject(link.attributes().asMap()).toString();
            jsonObject.addProperty("attributes", mapAsJson);
        }
        return jsonObject.toString();
    }

}
