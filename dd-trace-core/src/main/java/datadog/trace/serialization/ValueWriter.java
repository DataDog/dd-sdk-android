package datadog.trace.serialization;

import datadog.trace.serialization.EncodingCache;

// TODO @FunctionalInterface
public interface ValueWriter<T> {
  void write(T value, Writable writable, EncodingCache encodingCache);
}
