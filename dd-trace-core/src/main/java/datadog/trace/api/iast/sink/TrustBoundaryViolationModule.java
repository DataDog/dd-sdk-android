package datadog.trace.api.iast.sink;

import androidx.annotation.NonNull;

import datadog.trace.api.iast.IastModule;

public interface TrustBoundaryViolationModule extends IastModule {

  void onSessionValue(@NonNull String name, Object value);
}
