package com.datadog.trace.serialization;

import com.datadog.trace.serialization.Writable;

// TODO @FunctionalInterface
public interface Mapper<T> {
  void map(T data, Writable packer);
}
