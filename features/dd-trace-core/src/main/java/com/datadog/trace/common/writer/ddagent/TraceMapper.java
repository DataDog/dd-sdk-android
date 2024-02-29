package com.datadog.trace.common.writer.ddagent;

import com.datadog.trace.api.DDTags;
import com.datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import com.datadog.trace.common.writer.RemoteMapper;
import com.datadog.trace.core.DDSpanContext;

public interface TraceMapper extends RemoteMapper {

  UTF8BytesString THREAD_NAME = UTF8BytesString.create(DDTags.THREAD_NAME);
  UTF8BytesString THREAD_ID = UTF8BytesString.create(DDTags.THREAD_ID);
  UTF8BytesString SAMPLING_PRIORITY_KEY =
      UTF8BytesString.create(DDSpanContext.PRIORITY_SAMPLING_KEY);
  UTF8BytesString ORIGIN_KEY = UTF8BytesString.create(DDTags.ORIGIN_KEY);
}
