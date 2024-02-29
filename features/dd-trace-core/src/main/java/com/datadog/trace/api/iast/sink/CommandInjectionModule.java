package com.datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.datadog.trace.api.iast.IastModule;

public interface CommandInjectionModule extends IastModule {

  void onRuntimeExec(@NonNull String... command);

  void onRuntimeExec(@Nullable String[] env, @NonNull String... command);

  void onProcessBuilderStart(@NonNull List<String> command);
}
