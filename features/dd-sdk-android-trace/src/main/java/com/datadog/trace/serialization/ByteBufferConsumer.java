package com.datadog.trace.serialization;

import java.nio.ByteBuffer;

public interface ByteBufferConsumer {

  void accept(int messageCount, ByteBuffer buffer);
}
