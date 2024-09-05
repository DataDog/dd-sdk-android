package com.datadog.trace.api.gateway;

import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;

import java.util.Map;

public interface IGSpanInfo {
  DDTraceId getTraceId();

  long getSpanId();

  Map<String, Object> getTags();

  AgentSpan setTag(String key, boolean value);

  void setRequestBlockingAction(Flow.Action.RequestBlockingAction rba);

  Flow.Action.RequestBlockingAction getRequestBlockingAction();
}
