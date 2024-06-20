package com.datadog.trace.bootstrap.instrumentation.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Cache interface for OT/OTel wrappers used by TypeConverters to avoid extra allocations. */
public interface AttachableWrapper {

  /** Attaches a OT/OTel wrapper to a tracer object. */
  void attachWrapper(@NonNull Object wrapper);

  /** Returns an attached OT/OTel wrapper or null. */
  @Nullable
  Object getWrapper();
}
