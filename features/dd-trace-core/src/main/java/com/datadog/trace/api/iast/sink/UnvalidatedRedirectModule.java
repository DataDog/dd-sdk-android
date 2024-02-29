package com.datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;

import com.datadog.trace.api.iast.IastModule;

public interface UnvalidatedRedirectModule extends IastModule {

  void onRedirect(@Nullable String value);

  void onRedirect(@NonNull String value, @NonNull String clazz, @NonNull String method);

  void onURIRedirect(@Nullable URI value);

  void onHeader(@NonNull String name, String value);
}
