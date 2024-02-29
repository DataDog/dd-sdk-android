package com.datadog.trace.core.propagation;

import com.datadog.trace.api.TracePropagationStyle;
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.core.DDSpanContext;
import com.datadog.trace.core.datastreams.DataStreamContextInjector;

import java.util.LinkedHashMap;
import java.util.Map;

public class NoOpCorePropagation implements AgentPropagation {
  private final HttpCodec.Extractor extractor;

  public NoOpCorePropagation(HttpCodec.Extractor extractor) {
    this.extractor = extractor;
  }

  @Override
  public <C> void inject(final AgentSpan span, final C carrier, final Setter<C> setter) {
  }

  @Override
  public <C> void inject(AgentSpan.Context context, C carrier, Setter<C> setter) {
  }

  @Override
  public <C> void inject(AgentSpan span, C carrier, Setter<C> setter, TracePropagationStyle style) {
  }

  private <C> void inject(
      AgentSpan.Context context, C carrier, Setter<C> setter, TracePropagationStyle style) {
  }

  @Override
  public <C> void injectPathwayContext(
      AgentSpan span, C carrier, Setter<C> setter, LinkedHashMap<String, String> sortedTags) {
  }

  @Override
  public <C> void injectPathwayContext(
      AgentSpan span,
      C carrier,
      Setter<C> setter,
      LinkedHashMap<String, String> sortedTags,
      long defaultTimestamp,
      long payloadSizeBytes) {
  }

  @Override
  public <C> void injectPathwayContextWithoutSendingStats(
      AgentSpan span, C carrier, Setter<C> setter, LinkedHashMap<String, String> sortedTags) {
  }

  @Override
  public <C> AgentSpan.Context.Extracted extract(final C carrier, final ContextVisitor<C> getter) {
    return extractor.extract(carrier, getter);
  }
}
