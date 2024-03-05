package com.datadog.trace.core.tagprocessor;

import com.datadog.trace.api.DDTags;
import com.datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import com.datadog.trace.core.DDSpanContext;
import java.util.Map;
import androidx.annotation.Nullable;

public class BaseServiceAdder implements TagsPostProcessor {
  private final UTF8BytesString ddService;

  public BaseServiceAdder(@Nullable final String ddService) {
    this.ddService = ddService != null ? UTF8BytesString.create(ddService) : null;
  }

  @Override
  public Map<String, Object> processTags(Map<String, Object> unsafeTags) {
    // we are only able to do something if the span service name is known
    return unsafeTags;
  }

  @Override
  public Map<String, Object> processTagsWithContext(
      Map<String, Object> unsafeTags, DDSpanContext spanContext) {
    if (ddService != null && !ddService.toString().equalsIgnoreCase(spanContext.getServiceName())) {
      unsafeTags.put(DDTags.BASE_SERVICE, ddService);
    }
    return unsafeTags;
  }
}
