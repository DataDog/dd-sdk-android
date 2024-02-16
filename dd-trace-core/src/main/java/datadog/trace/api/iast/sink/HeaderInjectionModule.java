package datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import datadog.trace.api.iast.IastModule;

public interface HeaderInjectionModule extends IastModule {

  void onHeader(@NonNull String name, String value);
}
