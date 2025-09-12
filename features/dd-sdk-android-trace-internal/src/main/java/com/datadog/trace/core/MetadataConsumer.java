package com.datadog.trace.core;

import com.datadog.android.trace.internal.compat.function.Consumer;

@FunctionalInterface
public interface MetadataConsumer extends Consumer<Metadata> {

  MetadataConsumer NO_OP = (md) -> {};

  void accept(Metadata metadata);
}
