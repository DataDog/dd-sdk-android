package com.datadog.trace.core.propagation;

import com.datadog.trace.api.TracePropagationStyle;
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;

import java.util.LinkedHashMap;

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

  @Override
  public <C> AgentSpan.Context.Extracted extract(final C carrier, final ContextVisitor<C> getter) {
    return extractor.extract(carrier, getter);
  }
}
