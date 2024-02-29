package com.datadog.trace.serialization;

import com.datadog.trace.serialization.EncodingCache;

// TODO @FunctionalInterface
public interface ValueWriter<T> {
  void write(T value, Writable writable, EncodingCache encodingCache);
}
