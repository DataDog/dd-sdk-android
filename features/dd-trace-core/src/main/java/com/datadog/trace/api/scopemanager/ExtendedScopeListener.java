package com.datadog.trace.api.scopemanager;

import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.api.TraceConfig;

public interface ExtendedScopeListener extends ScopeListener {
  void afterScopeActivated(
      DDTraceId traceId, long localRootSpanId, long spanId, TraceConfig traceConfig);

  /** Called just after a scope is closed. */
  @Override
  void afterScopeClosed();
}
