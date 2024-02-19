package datadog.trace.serialization;

import datadog.trace.serialization.Writable;

// TODO @FunctionalInterface
public interface Mapper<T> {
  void map(T data, Writable packer);
}
