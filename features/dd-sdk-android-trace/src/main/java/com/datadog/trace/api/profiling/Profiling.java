package com.datadog.trace.api.profiling;

import com.datadog.trace.api.GlobalTracer;
import com.datadog.trace.api.Tracer;
import com.datadog.trace.api.internal.InternalTracer;

public interface Profiling {

  /**
   * Stateful API which restores the previous context when closed. This requires more memory so has
   * higher overhead than the stateless API.
   *
   * @return a profiling scope which can be closed to restore the current state.
   */
  default ProfilingScope newScope() {
    return ProfilingScope.NO_OP;
  }

  /**
   * Creates a decorator for the attribute, which can be used to set and clear contexts slightly
   * more efficiently than with string attributes.
   *
   * @param attribute the name of the attribute
   * @return a setter which can be used to set and clear profiling context
   */
  default ProfilingContextAttribute createContextAttribute(String attribute) {
    return ProfilingContextAttribute.NoOp.INSTANCE;
  }

  final class NoOp implements Profiling {
    public static final NoOp INSTANCE = new NoOp();
  }
}
