package com.datadog.trace.api.gateway;

import java.util.Map;

import com.datadog.trace.api.DDTraceId;
import com.datadog.trace.bootstrap.instrumentation.api.AgentSpan;

public interface IGSpanInfo {
  DDTraceId getTraceId();

  long getSpanId();

  Map<String, Object> getTags();

  AgentSpan setTag(String key, boolean value);

  void setRequestBlockingAction(Flow.Action.RequestBlockingAction rba);

  Flow.Action.RequestBlockingAction getRequestBlockingAction();
}
