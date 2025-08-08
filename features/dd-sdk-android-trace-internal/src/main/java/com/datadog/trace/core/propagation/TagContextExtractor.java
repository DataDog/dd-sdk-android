package com.datadog.trace.core.propagation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.datadog.android.trace.internal.compat.function.Supplier;
import com.datadog.trace.api.TraceConfig;
import com.datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import com.datadog.trace.bootstrap.instrumentation.api.TagContext;

public class TagContextExtractor implements HttpCodec.Extractor {

  private final Supplier<TraceConfig> traceConfigSupplier;
  private final ThreadLocal<ContextInterpreter> ctxInterpreter;
  private final ContextInterpreter.Factory factory;

  public TagContextExtractor(
      final Supplier<TraceConfig> traceConfigSupplier, final ContextInterpreter.Factory factory) {
    this.factory = factory;
    this.traceConfigSupplier = traceConfigSupplier;
    this.ctxInterpreter = new ThreadLocal<>();
  }

  @Override
  public void cleanup() {
    ctxInterpreter.remove();
  }

  @Override
  @Nullable
  public <C> TagContext extract(final C carrier, final AgentPropagation.ContextVisitor<C> getter) {
    ContextInterpreter interpreter = resolveContextInterpreter().reset(traceConfigSupplier.get());
    getter.forEachKey(carrier, interpreter);
    return interpreter.build();
  }

  @NonNull
  private ContextInterpreter resolveContextInterpreter() {
    ContextInterpreter contextInterpreter = ctxInterpreter.get();
    if (contextInterpreter == null) {
      contextInterpreter = factory.create();
      ctxInterpreter.set(contextInterpreter);
    }

    return contextInterpreter;
  }
}
