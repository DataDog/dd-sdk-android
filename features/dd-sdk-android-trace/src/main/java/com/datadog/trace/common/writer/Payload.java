package com.datadog.trace.common.writer;

import java.nio.ByteBuffer;

public abstract class Payload {

  private static final ByteBuffer EMPTY_ARRAY = ByteBuffer.allocate(1).put(0, (byte) 0x90);
  protected ByteBuffer body = EMPTY_ARRAY.duplicate();

}
