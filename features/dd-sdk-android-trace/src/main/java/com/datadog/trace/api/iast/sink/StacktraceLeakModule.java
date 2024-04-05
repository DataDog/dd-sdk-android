package com.datadog.trace.api.iast.sink;

import androidx.annotation.Nullable;

import com.datadog.trace.api.iast.IastModule;

public interface StacktraceLeakModule extends IastModule {
  void onStacktraceLeak(
      @Nullable final Throwable expression, String moduleName, String className, String methodName);
}