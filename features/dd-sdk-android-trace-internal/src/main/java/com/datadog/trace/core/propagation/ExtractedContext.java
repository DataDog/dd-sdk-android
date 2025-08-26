package com.datadog.trace.core.propagation;

import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.api.TraceConfig;
import com.datadog.trace.api.TracePropagationStyle;
import com.datadog.trace.api.sampling.PrioritySampling;
import com.datadog.trace.bootstrap.instrumentation.api.TagContext;

import java.util.Map;

/**
 * Propagated data resulting from calling tracer.extract with header data from an incoming request.
 */
public class ExtractedContext extends TagContext {
  private final DDTraceId traceId;
  private final long spanId;
  private final long endToEndStartTime;
  private final PropagationTags propagationTags;

  public ExtractedContext(
      final DDTraceId traceId,
      final long spanId,
      final int samplingPriority,
      final CharSequence origin,
      final PropagationTags propagationTags,
      final TracePropagationStyle propagationStyle) {
    this(
        traceId,
        spanId,
        samplingPriority,
        origin,
        0,
        null,
        null,
        null,
        propagationTags,
        null,
        propagationStyle);
  }

  public ExtractedContext(
      final DDTraceId traceId,
      final long spanId,
      final int samplingPriority,
      final CharSequence origin,
      final long endToEndStartTime,
      final Map<String, String> baggage,
      final Map<String, String> tags,
      final HttpHeaders httpHeaders,
      final PropagationTags propagationTags,
      final TraceConfig traceConfig,
      final TracePropagationStyle propagationStyle) {
    super(origin, tags, httpHeaders, baggage, samplingPriority, traceConfig, propagationStyle);
    this.traceId = traceId;
    this.spanId = spanId;
    this.endToEndStartTime = endToEndStartTime;
    this.propagationTags = propagationTags;
  }

  @Override
  public final DDTraceId getTraceId() {
    return traceId;
  }

  @Override
  public final long getSpanId() {
    return spanId;
  }

  public final long getEndToEndStartTime() {
    return endToEndStartTime;
  }

  public PropagationTags getPropagationTags() {
    return propagationTags;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("ExtractedContext{");
    if (traceId != null) {
      builder.append("traceId=").append(traceId).append(", ");
    }
    if (spanId != 0) {
      builder.append("endToEndStartTime=").append(spanId).append(", ");
    }
    if (endToEndStartTime != 0) {
      builder.append("spanId=").append(spanId).append(", ");
    }
    if (getOrigin() != null) {
      builder.append("origin=").append(getOrigin()).append(", ");
    }
    if (getTags() != null) {
      builder.append("tags=").append(getTags()).append(", ");
    }
    if (getBaggage() != null) {
      builder.append("baggage=").append(getBaggage()).append(", ");
    }
    if (getTraceSamplingPriority() != PrioritySampling.UNSET) {
      builder.append("samplingPriority=").append(getTraceSamplingPriority()).append(", ");
    }
    return builder.append('}').toString();
  }
}
