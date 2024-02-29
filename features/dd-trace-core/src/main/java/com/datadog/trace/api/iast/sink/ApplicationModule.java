package com.datadog.trace.api.iast.sink;

import androidx.annotation.Nullable;

import com.datadog.trace.api.iast.IastModule;

public interface ApplicationModule extends IastModule {

  void onRealPath(@Nullable String realPath);
}
