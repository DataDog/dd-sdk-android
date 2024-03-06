package com.datadog.trace.api.iast;

import androidx.annotation.Nullable;

import com.datadog.trace.api.gateway.RequestContext;
import com.datadog.trace.api.gateway.RequestContextSlot;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer;

/**
 * Initial encapsulation of the IAST context to be able to isolate it from the request. Ideally we
 * should move away from IastRequestContext and start using this interface as much as possible
 */
public interface IastContext {

  /**
   * Some scala instrumentations failed with public static methods inside an interface, that's the
   * reason behind an inner class.
   */
  abstract class Provider {

    private Provider() {}

    @Nullable
    public static IastContext get() {
      return get(AgentTracer.activeSpan());
    }

    @Nullable
    public static IastContext get(final AgentSpan span) {
      if (span == null) {
        return null;
      }
      return get(span.getRequestContext());
    }

    @Nullable
    public static IastContext get(final RequestContext reqCtx) {
      if (reqCtx == null) {
        return null;
      }
      return reqCtx.getData(RequestContextSlot.IAST);
    }
  }
}