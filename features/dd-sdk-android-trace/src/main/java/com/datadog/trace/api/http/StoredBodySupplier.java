package com.datadog.trace.api.http;

import androidx.annotation.NonNull;

import java.util.function.Supplier;

public interface StoredBodySupplier extends Supplier<CharSequence> {
  @NonNull
  CharSequence get();
}
