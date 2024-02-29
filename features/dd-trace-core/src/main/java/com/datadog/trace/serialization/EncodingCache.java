package com.datadog.trace.serialization;

// TODO @FunctionalInterface
public interface EncodingCache {

  byte[] encode(CharSequence s);
}
