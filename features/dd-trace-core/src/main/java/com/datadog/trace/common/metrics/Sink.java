package com.datadog.trace.common.metrics;

import com.datadog.trace.serialization.ByteBufferConsumer;

public interface Sink extends ByteBufferConsumer {

  void register(EventListener listener);
}
