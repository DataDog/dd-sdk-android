package com.datadog.trace.core.propagation;

import com.datadog.trace.api.TraceConfig;
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import com.datadog.trace.bootstrap.instrumentation.api.TagContext;

import com.datadog.android.trace.internal.compat.function.Supplier;

public class TagContextExtractor implements HttpCodec.Extractor {

  private final Supplier<TraceConfig> traceConfigSupplier;
  private final ThreadLocal<ContextInterpreter> ctxInterpreter;

  public TagContextExtractor(
      final Supplier<TraceConfig> traceConfigSupplier, final ContextInterpreter.Factory factory) {
    this.traceConfigSupplier = traceConfigSupplier;
    this.ctxInterpreter = new ThreadLocal<>();
    this.ctxInterpreter.set(factory.create());
  }

  @Override
  public <C> TagContext extract(final C carrier, final AgentPropagation.ContextVisitor<C> getter) {
    ContextInterpreter interpreter = this.ctxInterpreter.get().reset(traceConfigSupplier.get());
    getter.forEachKey(carrier, interpreter);
    return interpreter.build();
  }

  @Override
  public void cleanup() {
    ctxInterpreter.remove();
  }
}
