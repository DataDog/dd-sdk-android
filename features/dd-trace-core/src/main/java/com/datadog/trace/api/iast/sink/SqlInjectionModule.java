package com.datadog.trace.api.iast.sink;

import androidx.annotation.Nullable;

import com.datadog.trace.api.iast.IastModule;

public interface SqlInjectionModule extends IastModule {

  String DATABASE_PARAMETER = "DATABASE";

  void onJdbcQuery(@Nullable String sql);

  void onJdbcQuery(@Nullable String sql, @Nullable String database);
}
